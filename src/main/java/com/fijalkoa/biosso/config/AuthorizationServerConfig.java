package com.fijalkoa.biosso.config;

import com.fijalkoa.biosso.util.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;

import java.security.KeyPair;
import java.time.Duration;

/**
 * Spring Authorization Server Configuration
 * 
 * Replaces manual OAuth2/OIDC implementation with Spring's official authorization server
 * Automatically handles:
 * - Authorization Code Flow
 * - PKCE
 * - JWT Token Generation
 * - Refresh Token Rotation
 * - Token Revocation
 * - OpenID Connect Discovery
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    private final RsaKeyProvider rsaKeyProvider;
    private final JdbcTemplate jdbcTemplate;

    /**
     * RegisteredClientRepository - Client credentials storage
     * Uses database-backed JDBC repository for persistence
     * Supports both:
     * - Initial test client (hardcoded)
     * - Dynamically registered clients via OIDC DCR endpoint
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        log.info("📋 Initializing OAuth2 RegisteredClientRepository (JDBC-backed)...");

        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);

        // Check if test client already exists
        RegisteredClient existing = repository.findByClientId("89afnhi34oisdio203s");
        if (existing != null) {
            log.info("✅ Test client already registered (from database)");
            return repository;
        }

        // Initialize test client for local development
        RegisteredClient testClient = RegisteredClient.withId("89afnhi34oisdio203s")
                .clientId("89afnhi34oisdio203s")
                .clientSecret(passwordEncoder.encode("secret123"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:5000/auth/biosso-callback")
                .redirectUri("http://localhost:3000/auth/callback")
                .redirectUri("http://localhost:8080/login/oauth2/code/biosso")
                .scope("openid")
                .scope("profile")
                .scope("email")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .build())
                .build();

        repository.save(testClient);
        log.info("✅ Test client registered in database: 89afnhi34oisdio203s");

        return repository;

    /**
     * Authorization Server Settings
     * Configures issuer, endpoints, etc.
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:8080")
                .authorizationEndpoint("http://localhost:8080/oauth2/authorize")
                .tokenEndpoint("http://localhost:8080/oauth2/token")
                .tokenRevocationEndpoint("http://localhost:8080/oauth2/revoke")
                .tokenIntrospectionEndpoint("http://localhost:8080/oauth2/introspect")
                .jwkSetEndpoint("http://localhost:8080/oauth2/jwks")
                .oidcUserInfoEndpoint("http://localhost:8080/oauth2/userinfo")
                .oidcClientRegistrationEndpoint("http://localhost:8080/oauth2/register")
                .build();
    }
}
