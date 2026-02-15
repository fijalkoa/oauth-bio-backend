package com.fijalkoa.biosso.config;

import org.springframework.context.annotation.Configuration;

/**
 * WebSocket configuration is no longer used.
 * Biometric communication now uses REST endpoints instead.
 * 
 * Legacy WebSocket handler (FaceWebSocketHandler) is deprecated.
 * Use BiometricRestController for all biometric operations.
 */
@Configuration
public class WebSocketConfig {
    // Biometric operations now use REST API instead of WebSocket
}

