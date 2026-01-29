package com.fijalkoa.biosso.biometric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FaceWebSocketHandler extends BinaryWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RNG = new SecureRandom();
    private static final String MICROSERVICE_WS_URL = "ws://localhost:5001/ws";
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("[JAVA] client connected: " + session.getId());
        SessionState state = new SessionState();
        state.frontendSession = session;
        sessions.put(session.getId(), state);
        
        // Nawiąż połączenie z mikroserwisem Python
        connectToMicroservice(session.getId(), state);
    }

    private void connectToMicroservice(String sessionId, SessionState state) {
        try {
            HttpClient httpClient = HttpClient.newHttpClient();
            WebSocket microserviceWs = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(MICROSERVICE_WS_URL), new MicroserviceListener(sessionId, state))
                    .join();
            
            state.microserviceWs = microserviceWs;
            System.out.println("[JAVA] connected to microservice for session: " + sessionId);
        } catch (Exception e) {
            System.err.println("[JAVA] failed to connect to microservice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        System.out.println("[JAVA] received from frontend: " + payload);
        
        // Wyślij wiadomość do mikroserwisu
        SessionState state = sessions.get(session.getId());
        if (state != null && state.microserviceWs != null) {
            try {
                state.microserviceWs.sendText("Frontend message: " + payload, true);
                System.out.println("[JAVA] forwarded to microservice: " + payload);
            } catch (Exception e) {
                System.err.println("[JAVA] error sending to microservice: " + e.getMessage());
            }
        }
        
        try {
            if (payload.startsWith("{")) {
                JsonNode node = MAPPER.readTree(payload);
                if (node.has("type") && "meta".equals(node.get("type").asText())) {
                    String mode = node.has("mode") ? node.get("mode").asText() : null;
                    String step = node.has("step") ? node.get("step").asText(null) : null;
                    String userId = node.has("userId") ? node.get("userId").asText("unknown") : "unknown";
                    SessionState st = sessions.get(session.getId());

                    st.currentMeta = Map.of("mode", mode != null ? mode : "login", "step", step);
                    st.userId = userId;
                    
                    // Save registration data if present
                    if (node.has("registrationData")) {
                        st.registrationData = node.get("registrationData");
                        System.out.println("[JAVA] received registration data for user: " + userId);
                    }
                    
                    System.out.println("[JAVA] received meta: mode=" + mode + " step=" + step + " userId='" + userId + "'");
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[JAVA] error parsing meta message: " + e.getMessage());
            e.printStackTrace();
        }

        String data = payload;

        System.out.println("[JAVA] text message: " + data);
        if ("REGISTER_FINISHED".equals(data)) {

            try {
                session.sendMessage(new TextMessage("ENROLLMENT_OK"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        ByteBuffer payload = message.getPayload();
        byte[] bytes = new byte[payload.remaining()];
        payload.get(bytes);

        SessionState st = sessions.get(session.getId());
        if (st == null) {
            st = new SessionState();
            sessions.put(session.getId(), st);
        }

        if (bytes.length < 1) return;
        int header = bytes[0]; // 0=start,1=middle,2=end
        byte[] chunk = Arrays.copyOfRange(bytes, 1, bytes.length);

        if (header == 0) {
            st.resetBuffer();
            st.append(chunk);
        } else if (header == 1) {
            st.append(chunk);
        } else if (header == 2) {
            st.append(chunk);
            byte[] full = st.toByteArray();
            // save image to disk (optional)
            String filename = "received_" + session.getId() + "_" + System.currentTimeMillis() + ".png";
            Files.write(Paths.get(filename), full);
            System.out.println("[JAVA] received full image saved: " + filename + " size=" + full.length);

            // process according to last meta
            Map<String, String> meta = st.currentMeta != null ? st.currentMeta : Map.of("mode", "login", "step", "0");
            String mode = meta.getOrDefault("mode", "login");
            String step = meta.get("step");

            if (st.currentMeta == null) {
                System.out.println("[JAVA] WARNING: currentMeta is null, using default (login mode). userId=" + st.userId);
            }
            System.out.println("[JAVA] processing image for mode=" + mode + " step=" + step);

            if ("login".equalsIgnoreCase(mode)) {
                // Send image to Python microservice for verification
                sendImageToMicroservice(session, full, mode, step, st.userId);
            } else if ("register".equalsIgnoreCase(mode)) {
                // store the image
                st.enrolledImages.add(full);

                // if this is first image (step=0 or null), send next MOVE_HEAD
                if (st.enrolledImages.size() == 1) {
                    // generate random sequence of 4 moves
                    st.moveSequence = generateRandomMoveSequence(4);
                    st.moveIndex = 0;
                }

                // if we just received a movement capture (step equals movement name), advance
                if (step != null && !step.equals("0")) {
                    // step will be movement name (LEFT/RIGHT/UP/DOWN)
                    st.moveIndex = Math.min(st.moveIndex + 1, st.moveSequence.size());
                }

                if (st.moveIndex < st.moveSequence.size()) {
                    String nextMove = st.moveSequence.get(st.moveIndex);
                    session.sendMessage(new TextMessage("MOVE_HEAD:" + nextMove));
                    System.out.println("[JAVA] ask move: " + nextMove);
                } else {
                    // finished - send all data to Python
                    System.out.println("[JAVA] enrollment finished, sending data to microservice. Total images: " + st.enrolledImages.size());
                    sendRegistrationDataToMicroservice(session, st);
                    st.enrolledImages.clear();
                }
            } else {
                session.sendMessage(new TextMessage("UNKNOWN_MODE"));
            }

            // clear currentMeta so next meta expected
            st.currentMeta = null;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        SessionState state = sessions.get(session.getId());
        if (state != null && state.microserviceWs != null) {
            try {
                state.microserviceWs.sendClose(WebSocket.NORMAL_CLOSURE, "Frontend closed").join();
            } catch (Exception e) {
                System.err.println("[JAVA] error closing microservice connection: " + e.getMessage());
            }
        }
        sessions.remove(session.getId());
        System.out.println("[JAVA] connection closed: " + session.getId());
    }

    private void sendRegistrationDataToMicroservice(WebSocketSession session, SessionState state) {
        if (state == null || state.microserviceWs == null) {
            System.err.println("[JAVA] microservice connection not available");
            return;
        }

        try {
            // Encode all images to Base64
            List<String> base64Images = new ArrayList<>();
            for (byte[] imageData : state.enrolledImages) {
                base64Images.add(Base64.getEncoder().encodeToString(imageData));
            }
            
            // Create JSON message with registration data and images
            Map<String, Object> message = new HashMap<>();
            message.put("type", "register");
            message.put("userId", state.userId);
            message.put("images", base64Images);
            message.put("userData", state.registrationData);
            
            String jsonMessage = MAPPER.writeValueAsString(message);
            state.microserviceWs.sendText(jsonMessage, true);
            System.out.println("[JAVA] sent registration data to microservice: userId=" + state.userId + " images=" + base64Images.size());
        } catch (Exception e) {
            System.err.println("[JAVA] error sending registration data to microservice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendImageToMicroservice(WebSocketSession session, byte[] imageData, String mode, String step, String userId) {
        SessionState state = sessions.get(session.getId());
        if (state == null || state.microserviceWs == null) {
            System.err.println("[JAVA] microservice connection not available");
            return;
        }

        try {
            // Encode image to Base64
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            
            // Create JSON message with image and metadata
            Map<String, Object> message = new HashMap<>();
            message.put("type", "image");
            message.put("mode", mode);
            message.put("step", step);
            message.put("userId", userId);
            message.put("payload", base64Image);
            
            String jsonMessage = MAPPER.writeValueAsString(message);
            state.microserviceWs.sendText(jsonMessage, true);
            System.out.println("[JAVA] sent image to microservice: mode=" + mode + " userId=" + userId);
        } catch (Exception e) {
            System.err.println("[JAVA] error sending image to microservice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<String> generateRandomMoveSequence(int n) {
        List<String> moves = new ArrayList<>(List.of("LEFT", "RIGHT", "UP", "DOWN"));
        Collections.shuffle(moves, RNG);
        if (n <= moves.size()) {
            return moves.subList(0, n);
        } else {
            // repeat if needed
            List<String> out = new ArrayList<>();
            for (int i = 0; i < n; i++) out.add(moves.get(i % moves.size()));
            return out;
        }
    }

    // SessionState inner class
    private static class SessionState {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Map<String, String> currentMeta = null;
        List<byte[]> enrolledImages = new ArrayList<>();
        List<String> moveSequence = new ArrayList<>();
        int moveIndex = 0;
        WebSocket microserviceWs = null;
        String userId = "unknown";
        WebSocketSession frontendSession = null;
        JsonNode registrationData = null;

        void append(byte[] chunk) {
            try {
                buffer.write(chunk);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        byte[] toByteArray() {
            return buffer.toByteArray();
        }

        void resetBuffer() {
            buffer.reset();
        }
    }

    // Listener dla połączenia z mikroserwisem
    private static class MicroserviceListener implements WebSocket.Listener {
        private final String sessionId;
        private final SessionState state;

        MicroserviceListener(String sessionId, SessionState state) {
            this.sessionId = sessionId;
            this.state = state;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("[JAVA] microservice connection opened for session: " + sessionId);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            System.out.println("[JAVA] received from microservice (session " + sessionId + "): " + data);
            
            // Forward response to frontend if it's a result message
            try {
                String dataStr = data.toString();
                if (dataStr.startsWith("{")) {
                    JsonNode node = MAPPER.readTree(dataStr);
                    if (node.has("type")) {
                        String type = node.get("type").asText();
                        
                        if ("result".equals(type)) {
                            // This is a verification result, forward to frontend
                            String status = node.path("status").asText("rejected");
                            String responseToFrontend = "true".equals(status) || "verified".equals(status) ? "true" : "false";
                            
                            if (state.frontendSession != null && state.frontendSession.isOpen()) {
                                state.frontendSession.sendMessage(new TextMessage(responseToFrontend));
                                System.out.println("[JAVA] forwarded result to frontend: " + responseToFrontend);
                            }
                        } else if ("registration_result".equals(type)) {
                            // This is a registration result
                            String status = node.path("status").asText("error");
                            
                            if (state.frontendSession != null && state.frontendSession.isOpen()) {
                                state.frontendSession.sendMessage(new TextMessage("ENROLLMENT_OK"));
                                System.out.println("[JAVA] forwarded registration result to frontend: " + status);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[JAVA] error processing microservice response: " + e.getMessage());
            }
            
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("[JAVA] microservice error (session " + sessionId + "): " + error.getMessage());
            error.printStackTrace();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("[JAVA] microservice connection closed (session " + sessionId + "): " + reason);
            return null;
        }
    }
}
