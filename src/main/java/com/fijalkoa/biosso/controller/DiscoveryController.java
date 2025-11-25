package com.fijalkoa.biosso.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class DiscoveryController {

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> configuration() {
        Map<String, Object> config = new HashMap<>();
        config.put("issuer", "http://localhost:8080");
        config.put("authorization_endpoint", "http://localhost:8080/oauth2/authorize");
        config.put("token_endpoint", "http://localhost:8080/oauth2/token");
        config.put("userinfo_endpoint", "http://localhost:8080/oauth2/userinfo");
        config.put("jwks_uri", "http://localhost:8080/oauth2/jwks");
        config.put("response_types_supported", List.of("code"));
        config.put("grant_types_supported", List.of("authorization_code"));
        config.put("scopes_supported", List.of("openid", "email", "profile"));
        config.put("id_token_signing_alg_values_supported", List.of("RS256"));
        return config;
    }
}

