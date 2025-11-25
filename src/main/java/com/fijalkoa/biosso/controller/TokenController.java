package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.service.AuthorizationCodeService;
import com.fijalkoa.biosso.service.ClientService;
import com.fijalkoa.biosso.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class TokenController {

    private final AuthorizationCodeService codeService;
    private final ClientService clientService;
    private final JwtUtil jwtUtil;


    @PostMapping("/token")
    public ResponseEntity<?> exchangeCode(
            @RequestParam String grant_type,
            @RequestParam String code,
            @RequestParam String redirect_uri,
            @RequestParam String client_id,
            @RequestParam(required = false) String code_verifier,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!"authorization_code".equals(grant_type)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }

        String clientSecret = null;
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            // confidential client flow
            String[] creds = decodeBasicAuth(authHeader);
            clientSecret = creds[1];

            if (!clientService.validateClientCredentials(client_id, clientSecret)) {
                return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
            }
        }

        var data = codeService.consumeCode(code, client_id, redirect_uri);
        if (data == null) {
            return ResponseEntity.status(400).body(Map.of("error", "invalid_code"));
        }

        // --- PKCE verification if code_challenge present ---
        if (data.codeChallenge() != null) {
            if (code_verifier == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "missing_code_verifier"));
            }

            try {
                String computedChallenge;
                if ("S256".equalsIgnoreCase(data.codeChallengeMethod())) {
                    computedChallenge = Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(MessageDigest.getInstance("SHA-256")
                                    .digest(code_verifier.getBytes(StandardCharsets.US_ASCII)));
                } else {
                    computedChallenge = code_verifier;
                }

                if (!computedChallenge.equals(data.codeChallenge())) {
                    return ResponseEntity.status(400).body(Map.of("error", "invalid_code_verifier"));
                }
            } catch (Exception e) {
                return ResponseEntity.status(500).body(Map.of("error", "pkce_verification_failed"));
            }
        }

        User user = data.user();

        String accessToken = jwtUtil.generateAccessToken(user);
        String idToken = jwtUtil.generateIdToken(user, client_id, data.nonce());
        String refreshToken = UUID.randomUUID().toString();

        return ResponseEntity.ok(Map.of(
                "access_token", accessToken,
                "id_token", idToken,
                "token_type", "Bearer",
                "expires_in", 3600,
                "refresh_token", refreshToken
        ));
    }

    private String[] decodeBasicAuth(String header) {
        String base64 = header.replace("Basic ", "");
        return new String(Base64.getDecoder().decode(base64)).split(":", 2);
    }
}

