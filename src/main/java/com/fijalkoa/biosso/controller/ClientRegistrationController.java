package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.service.ClientRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OIDC Dynamic Client Registration Controller
 * 
 * Implements OpenID Connect Dynamic Client Registration 1.0
 * https://openid.net/specs/openid-connect-registration-1_0.html
 * 
 * Allows relying parties to self-register without manual setup.
 * This makes BioSSO a true multi-tenant Identity Provider.
 * 
 * Usage:
 * POST /oauth2/register
 * Content-Type: application/json
 * 
 * {
 *   "client_name": "My Awesome App",
 *   "redirect_uris": ["https://myapp.com/auth/callback"],
 *   "response_types": ["code"],
 *   "grant_types": ["authorization_code", "refresh_token"],
 *   "token_endpoint_auth_method": "client_secret_basic",
 *   "require_pkce": true,
 *   "scopes": ["openid", "profile", "email"]
 * }
 */
@Slf4j
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class ClientRegistrationController {

    private final ClientRegistrationService registrationService;

    /**
     * OIDC Dynamic Client Registration Endpoint
     * 
     * Relying parties POST their registration metadata to receive:
     * - client_id
     * - client_secret
     * - registration_access_token (for management)
     * 
     * Response: 201 Created
     * 
     * Example request:
     * {
     *   "client_name": "My Application",
     *   "redirect_uris": ["https://myapp.com/auth/biosso-callback"],
     *   "response_types": ["code"],
     *   "grant_types": ["authorization_code", "refresh_token"],
     *   "token_endpoint_auth_method": "client_secret_basic",
     *   "require_pkce": true,
     *   "scopes": ["openid", "profile", "email"]
     * }
     * 
     * Example response:
     * {
     *   "client_id": "client_a1b2c3d4e5f6g7h8",
     *   "client_secret": "secret_xyz...",
     *   "client_name": "My Application",
     *   "redirect_uris": ["https://myapp.com/auth/biosso-callback"],
     *   "token_endpoint_auth_method": "client_secret_basic",
     *   "registration_access_token": "reg_...",
     *   "registration_client_uri": "http://...../oauth2/clients/client_a1b2c3d4e5f6g7h8",
     *   "client_id_issued_at": 1708161234,
     *   "client_secret_expires_at": 0
     * }
     */
    @PostMapping(
            value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> registerClient(@RequestBody Map<String, Object> registrationRequest) {
        log.info("📋 OIDC Dynamic Client Registration request received");

        try {
            Map<String, Object> response = registrationService.registerClient(registrationRequest);
            log.info("✅ Client registered successfully");
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️  Invalid registration request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "error_description", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Client registration failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "server_error",
                    "error_description", "Failed to register client: " + e.getMessage()
            ));
        }
    }
}
