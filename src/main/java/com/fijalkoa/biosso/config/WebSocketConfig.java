package com.fijalkoa.biosso.config;

import com.fijalkoa.biosso.biometric.FaceWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        FaceWebSocketHandler faceHandler = new FaceWebSocketHandler();
        registry.addHandler(faceHandler, "/ws/face")
                .setAllowedOrigins("*");
    }
}
