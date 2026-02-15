package com.fijalkoa.biosso.biometric;

import lombok.extern.slf4j.Slf4j;

/**
 * DEPRECATED - WebSocket handler is no longer used
 * 
 * Biometric communication has been migrated to REST API.
 * Use BiometricController and BiometricRestService instead.
 * 
 * This class is kept for reference only.
 */
@Slf4j
@Deprecated(since = "2.0.0", forRemoval = true)
public class FaceWebSocketHandler {

    public FaceWebSocketHandler() {
        log.warn("⚠️ FaceWebSocketHandler is deprecated and should not be instantiated");
        log.warn("   Use BiometricController (/api/biometric/register, /api/biometric/verify) instead");
    }
}

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
        System.out.println("[JAVA] received text message from frontend: " + payload.substring(0, Math.min(100, payload.length())));
        
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            state = new SessionState();
            sessions.put(session.getId(), state);
        }

        try {
            if (payload.startsWith("{")) {
                JsonNode node = MAPPER.readTree(payload);
                
                // Extract metadata
                if (node.has("type") && "meta".equals(node.get("type").asText())) {
                    String mode = node.has("mode") ? node.get("mode").asText("login") : "login";
                    String step = node.has("step") ? node.get("step").asText(null) : null;
                    String userId = node.has("userId") ? node.get("userId").asText("unknown") : "unknown";
                    
                    state.currentMeta = Map.of("mode", mode, "step", step != null ? step : "");
                    state.userId = userId;
                    
                    System.out.println("[JAVA] received metadata: mode=" + mode + " step=" + step + " userId=" + userId);
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[JAVA] error parsing message: " + e.getMessage());
        }

        // Simple text commands
        if ("REGISTER_FINISHED".equals(payload)) {
            System.out.println("[JAVA] register finished signal received");
            try {
                session.sendMessage(new TextMessage("ENROLLMENT_OK"));
            } catch (IOException e) {
                System.err.println("[JAVA] error sending ENROLLMENT_OK: " + e.getMessage());
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
        
        int header = bytes[0]; // 0=start, 1=middle, 2=end
        byte[] chunk = Arrays.copyOfRange(bytes, 1, bytes.length);

        if (header == 0) {
            st.resetBuffer();
            st.append(chunk);
        } else if (header == 1) {
            st.append(chunk);
        } else if (header == 2) {
            st.append(chunk);
            byte[] imageData = st.toByteArray();
            
            // Log that image was received (for session metadata only)
            st.imagesReceived++;
            System.out.println("[JAVA] received image " + st.imagesReceived + 
                    " for session: " + session.getId() + 
                    " size=" + imageData.length);

            // Process according to metadata
            Map<String, String> meta = st.currentMeta != null ? st.currentMeta : Map.of("mode", "login");
            String mode = meta.getOrDefault("mode", "login");
            String step = meta.get("step");

            System.out.println("[JAVA] processing image for mode=" + mode + " step=" + step);

            if ("login".equalsIgnoreCase(mode)) {
                // Send image to Python microservice for verification
                sendImageToMicroservice(session, imageData, mode, step, st.userId);
            } else if ("register".equalsIgnoreCase(mode)) {
                // For registration, collect images and manage sequence
                st.enrolledImages.add(imageData);

                if (st.enrolledImages.size() == 1) {
                    st.moveSequence = generateRandomMoveSequence(4);
                    st.moveIndex = 0;
                }

                if (step != null && !step.equals("0")) {
                    st.moveIndex = Math.min(st.moveIndex + 1, st.moveSequence.size());
                }

                if (st.moveIndex < st.moveSequence.size()) {
                    String nextMove = st.moveSequence.get(st.moveIndex);
                    session.sendMessage(new TextMessage("MOVE_HEAD:" + nextMove));
                    System.out.println("[JAVA] requesting move: " + nextMove);
                } else {
                    // All images collected, send to Python for processing
                    System.out.println("[JAVA] enrollment phase complete, images collected: " + st.enrolledImages.size());
                    sendRegistrationDataToMicroservice(session, st);
                    st.enrolledImages.clear();
                }
            } else {
                session.sendMessage(new TextMessage("ERROR_UNKNOWN_MODE"));
            }

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
            System.err.println("[JAVA] microservice connection not available for registration");
            return;
        }

        try {
            // Encode all collected images to Base64
            List<String> base64Images = new ArrayList<>();
            for (byte[] imageData : state.enrolledImages) {
                base64Images.add(Base64.getEncoder().encodeToString(imageData));
            }
            
            // Create registration message for Python microservice
            // Python will handle all encryption and storage
            Map<String, Object> message = new HashMap<>();
            message.put("type", "register");
            message.put("userId", state.userId);
            message.put("images", base64Images);
            message.put("moveSequence", state.moveSequence);
            
            String jsonMessage = MAPPER.writeValueAsString(message);
            state.microserviceWs.sendText(jsonMessage, true);
            System.out.println("[JAVA] ✅ sent registration data to Python: userId=" + state.userId + 
                    " images=" + base64Images.size());
        } catch (Exception e) {
            System.err.println("[JAVA] ❌ error sending registration data to microservice: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendImageToMicroservice(WebSocketSession session, byte[] imageData, String mode, String step, String userId) {
        SessionState state = sessions.get(session.getId());
        if (state == null || state.microserviceWs == null) {
            System.err.println("[JAVA] microservice connection not available for verification");
            return;
        }

        try {
            // Encode image to Base64 for transmission
            String base64Image = Base64.getEncoder().encodeToString(imageData);
            
            // Create verification message for Python microservice
            // Python will handle all decryption and matching
            Map<String, Object> message = new HashMap<>();
            message.put("type", "verify");
            message.put("mode", mode);
            message.put("step", step);
            message.put("userId", userId);
            message.put("payload", base64Image);
            
            String jsonMessage = MAPPER.writeValueAsString(message);
            state.microserviceWs.sendText(jsonMessage, true);
            System.out.println("[JAVA] ✅ sent verification image to Python: userId=" + userId);
        } catch (Exception e) {
            System.err.println("[JAVA] ❌ error sending image to microservice: " + e.getMessage());
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

    // SessionState inner class - maintains state for current WebSocket connection
    private static class SessionState {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Map<String, String> currentMeta = null;
        List<byte[]> enrolledImages = new ArrayList<>();  // For registration: collected images before sending to Python
        List<String> moveSequence = new ArrayList<>();     // For registration: head movement sequence
        int moveIndex = 0;
        WebSocket microserviceWs = null;
        String userId = "unknown";
        WebSocketSession frontendSession = null;
        int imagesReceived = 0;

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
