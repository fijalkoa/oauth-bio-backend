package com.fijalkoa.biosso.biometric;

import jakarta.websocket.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@ClientEndpoint
public class WsClientApp {

    private Session session;
    private final CountDownLatch latch = new CountDownLatch(1);

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("[CLIENT] Connected to server: " + session.getRequestURI());
        this.session = session;
    }

    @OnMessage
    public void onTextMessage(String message) {
        System.out.println("[CLIENT] Received message: " + message);
        latch.countDown();
    }

    @OnMessage
    public void onBinaryMessage(ByteBuffer msg) {
        System.out.println("[CLIENT] Received binary message of " + msg.remaining() + " bytes");
    }

    @OnClose
    public void onClose(CloseReason reason) {
        System.out.println("[CLIENT] Connection closed: " + reason);
        latch.countDown();
    }

    @OnError
    public void onError(Throwable t) {
        System.err.println("[CLIENT] Error: " + t.getMessage());
        t.printStackTrace();
        latch.countDown();
    }

    public void sendImage(Path imagePath) throws Exception {
        byte[] bytes = Files.readAllBytes(imagePath);
        System.out.println("[CLIENT] Sending image " + imagePath + " (" + bytes.length + " bytes)...");
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(bytes));
        latch.await(10, TimeUnit.SECONDS);
    }

    public void sendImage(byte[] bytes) throws Exception {
        System.out.println("[CLIENT] Sending image (" + bytes.length + " bytes)...");
        session.getBasicRemote().sendBinary(ByteBuffer.wrap(bytes));
        latch.await(10, TimeUnit.SECONDS);
    }

    public void connectAndSendImage(String serverUrl, byte[] bytes) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, URI.create(serverUrl));

        Thread.sleep(500);
        sendImage(bytes);

        if (session != null && session.isOpen()) {
            session.close();
        }
    }
}
