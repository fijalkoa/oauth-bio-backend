package com.fijalkoa.biosso.biometric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FaceWebSocketHandler extends BinaryWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RNG = new SecureRandom();
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("[JAVA] client connected: " + session.getId());
        sessions.put(session.getId(), new SessionState());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        try {
            if (payload.startsWith("{")) {
                JsonNode node = MAPPER.readTree(payload);
                if (node.has("type") && "meta".equals(node.get("type").asText())) {
                    String mode = node.has("mode") ? node.get("mode").asText() : node.has("mode") ? node.get("mode").asText() : null;
                    String step = node.has("step") ? node.get("step").asText(null) : null;
                    SessionState st = sessions.get(session.getId());

                    st.currentMeta = Map.of("mode", node.path("mode").asText("login"), "step", step);
                    System.out.println("[JAVA] received meta: " + st.currentMeta);
                    return;
                }
            }
        } catch (Exception e) {

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

            System.out.println("[JAVA] processing image for mode=" + mode + " step=" + step);

            if ("login".equalsIgnoreCase(mode)) {
                // random accept/reject
                boolean accept = RNG.nextBoolean();
                String resp = accept ? "true" : "false";
                session.sendMessage(new TextMessage(resp));
                System.out.println("[JAVA] login -> " + resp);
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
                    // finished
                    session.sendMessage(new TextMessage("ENROLLMENT_OK"));
                    System.out.println("[JAVA] enrollment finished for session " + session.getId());
                    // optionally persist enrolled images or process embeddings here
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
        sessions.remove(session.getId());
        System.out.println("[JAVA] connection closed: " + session.getId());
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
}
