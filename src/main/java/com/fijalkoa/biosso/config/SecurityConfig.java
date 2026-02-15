package com.fijalkoa.biosso.config;

import com.fijalkoa.biosso.security.BioSSOAuthenticationProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Security Configuration for BioSSO
 * 
 * Integrates:
 * - Spring Authorization Server for OAuth2/OIDC
 * - CORS for cross-origin requests
 * - Custom biometric authentication provider
 * - Form login for password authentication
 * - Logout endpoint with token revocation
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final BioSSOAuthenticationProvider bioSSOAuthenticationProvider;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authManagerBuilder.authenticationProvider(bioSSOAuthenticationProvider);
        return authManagerBuilder.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Security Filter Chain
     * 
     * Configuration for Spring Authorization Server integration:
     * - CORS enabled for cross-origin OAuth2 requests
     * - Authorization endpoints protected
     * - Form login for password-based authentication
     * - Biometric verification for enrolled users
     * - Refresh token endpoint access
     * - Logout with token revocation
     * 
     * Authentication Levels:
     * - PUBLIC: Unauthenticated users (login, registration, OIDC discovery)
     * - BIOMETRIC_PENDING: Users who passed password auth but need biometric verification
     * - AUTHENTICATED: Users fully authenticated (password + biometric if required)
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - anyone can access
                        .requestMatchers(
                                "/login",
                                "/register",
                                "/css/**", "/js/**",
                                "/images/**",
                                "/.well-known/openid-configuration",
                                "/.well-known/oauth-authorization-server",
                                "/oauth2/**",
                                "/api/biometric/register",    // Public registration
                                "/api/biometric/health",      // Public health check
                                "/api/biometric/metrics",     // Public metrics
                                "/error",
                                "/actuator/health"
                        )
                        .permitAll()
                        // Biometric verification endpoint - requires BIOMETRIC_VERIFICATION_REQUIRED authority
                        .requestMatchers("/api/biometric/verify-for-auth")
                        .hasAuthority("BIOMETRIC_VERIFICATION_REQUIRED")
                        // Standalone verify endpoint - public (can be used without auth)
                        .requestMatchers("/api/biometric/verify")
                        .permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Form login configuration - required for password authentication
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/doLogin")
                        .defaultSuccessUrl("/")
                        .permitAll()
                )
                // Logout configuration
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                // Exception handling
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("🔐 Unauthorized access attempt: {}", authException.getMessage());
                            response.sendRedirect("/login");
                        })
                );

        return http.build();
    }
}
