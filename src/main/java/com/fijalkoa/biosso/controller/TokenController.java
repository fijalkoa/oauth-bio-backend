package com.fijalkoa.biosso.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @deprecated TokenController is deprecated
 * 
 * This controller is no longer used. Spring Authorization Server (SAS) provides the token endpoint
 * automatically at /oauth2/token
 * 
 * SAS handles:
 * - Authorization Code Grant with PKCE
 * - Refresh Token Grant
 * - Token validation
 * - JWT generation with RS256
 * 
 * This stub remains for documentation purposes only.
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
public class TokenController {

    /**
     * Deprecated token endpoint - use Spring Authorization Server instead
     */
    @PostMapping("/token")
    public ResponseEntity<?> tokenDeprecated() {
        log.warn("⚠️ Deprecated: /oauth2/token called. Spring Authorization Server handles token exchange.");
        return ResponseEntity
                .status(404)
                .body(Map.of(
                        "error", "deprecated",
                        "message", "Use Spring Authorization Server /oauth2/token endpoint instead"
                ));
    }
}

