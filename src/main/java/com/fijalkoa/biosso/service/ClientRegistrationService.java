package com.fijalkoa.biosso.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OIDC Dynamic Client Registration Service
 * 
 * Handles client registration requests from relying parties
 * per OpenID Connect Dynamic Client Registration 1.0 specification
 * 
 * Allows relying parties to self-register without manual setup.
 * Persists clients to database for SAS to retrieve and validate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientRegistrationService {

    private final PasswordEncoder passwordEncoder;
    private final RegisteredClientRepository registeredClientRepository;

    @Value("${app.oauth.issuer:http://localhost:8080}")
    private String issuer;

    /**
     * Register a new OAuth2 client using OIDC DCR
     * 
     * Validates input, generates credentials, persists to database,
     * and returns registration response per OIDC spec.
     * 
     * Request payload example:
     * {
     *   "client_name": "My App",
     *   "redirect_uris": ["https://myapp.com/auth/callback"],
     *   "response_types": ["code"],
     *   "grant_types": ["authorization_code", "refresh_token"],
     *   "token_endpoint_auth_method": "client_secret_basic",
     *   "require_pkce": true,
     *   "scopes": ["openid", "profile", "email"]
     * }
     */
    public Map<String, Object> registerClient(Map<String, Object> registrationRequest) {
        log.info("📋 Processing OIDC Dynamic Client Registration request");

        // Extract and validate parameters
        String clientName = (String) registrationRequest.get("client_name");
        if (clientName == null || clientName.isEmpty()) {
            throw new IllegalArgumentException("client_name is required");
        }

        @SuppressWarnings("unchecked")
        var redirectUris = (List<String>) registrationRequest.get("redirect_uris");
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new IllegalArgumentException("redirect_uris is required (must have at least one URI)");
        }

        // Validate each redirect URI
        for (String uri : redirectUris) {
            if (!isValidRedirectUri(uri)) {
                throw new IllegalArgumentException("Invalid redirect URI: " + uri);
            }
        }

        // Generate client credentials
        String clientId = generateClientId();
        String clientSecret = generateClientSecret();
        String internalId = UUID.randomUUID().toString();

        log.info("✅ Registering new OAuth2 client: {} ({})", clientName, clientId);

        // Build RegisteredClient for persistence
        RegisteredClient registeredClient = RegisteredClient.withId(internalId)
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName(clientName)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .scope("openid")
                .scope("profile")
                .scope("email")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)  // PKCE always required
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        // Save to database
        try {
            registeredClientRepository.save(registeredClient);
            log.info("✅ Client persisted to database: {}", clientId);
        } catch (Exception e) {
            log.error("❌ Failed to persist client to database", e);
            throw new RuntimeException("Failed to save client: " + e.getMessage(), e);
        }

        // Build response per OIDC DCR spec
        @SuppressWarnings("unchecked")
        List<String> scopes = (List<String>) registrationRequest.getOrDefault("scopes", 
                new ArrayList<>(List.of("openid", "profile", "email")));

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("client_id", clientId);
        response.put("client_secret", clientSecret);
        response.put("client_name", clientName);
        response.put("redirect_uris", redirectUris);
        response.put("response_types", new String[]{"code"});
        response.put("grant_types", new String[]{"authorization_code", "refresh_token"});
        response.put("token_endpoint_auth_method", "client_secret_basic");
        response.put("require_pkce", true);
        response.put("scope", String.join(" ", scopes));
        response.put("registration_access_token", generateAccessToken());
        response.put("registration_client_uri", issuer + "/oauth2/clients/" + clientId);
        response.put("client_id_issued_at", System.currentTimeMillis() / 1000);
        response.put("client_secret_expires_at", 0);  // Never expires

        log.info("📤 OIDC DCR response ready for client: {}", clientId);
        return response;
    }

    /**
     * Validate redirect URI format
     * - Must be HTTPS for production (HTTP allowed for localhost)
     * - Must be valid URI format
     * - Cannot contain fragments (#)
     * - Cannot be a wildcard (specific URI required)
     */
    private boolean isValidRedirectUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }

        try {
            java.net.URL url = new java.net.URL(uri);
            
            // Check scheme
            boolean isValidScheme = "https".equals(url.getProtocol()) || 
                                  ("http".equals(url.getProtocol()) && 
                                   (url.getHost().equals("localhost") || url.getHost().equals("127.0.0.1")));
            
            if (!isValidScheme) {
                log.warn("❌ Redirect URI scheme not allowed: {} (must be https, or http://localhost)", uri);
                return false;
            }
            
            // Cannot have fragment
            if (uri.contains("#")) {
                log.warn("❌ Redirect URI cannot contain fragment: {}", uri);
                return false;
            }
            
            // Cannot be wildcard
            if (uri.contains("*")) {
                log.warn("❌ Redirect URI cannot contain wildcard: {}", uri);
                return false;
            }
            
            return true;
        } catch (java.net.MalformedURLException e) {
            log.warn("❌ Invalid redirect URI format: {}", uri);
            return false;
        }
    }

    /**
     * Generate a cryptographically secure client ID
     * Format: client_<random>
     */
    private String generateClientId() {
        return "client_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    /**
     * Generate a cryptographically secure client secret
     * Combines two UUIDs for extra entropy
     */
    private String generateClientSecret() {
        return UUID.randomUUID().toString().replace("-", "") + 
               UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generate registration access token for client management
     * (Could be used for future client update/delete operations)
     */
    private String generateAccessToken() {
        return "reg_" + UUID.randomUUID().toString().replace("-", "");
    }
}
