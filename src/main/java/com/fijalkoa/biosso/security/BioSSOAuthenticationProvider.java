package com.fijalkoa.biosso.security;

import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.model.UserBiometricMetadata;
import com.fijalkoa.biosso.repository.UserBiometricMetadataRepository;
import com.fijalkoa.biosso.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BioSSO Authentication Provider with Biometric Integration
 * 
 * Authentication Flow:
 * 1. Verify email + password (normal authentication)
 * 2. Check if user has ACTIVE biometric enrollment
 * 3. If enrolled:
 *    - Return BiometricPendingAuthenticationToken
 *    - Frontend shows biometric capture UI
 *    - User calls /api/biometric/verify with images
 * 4. If not enrolled:
 *    - Return full UsernamePasswordAuthenticationToken
 *    - User proceeds straight to OAuth2 flow
 * 
 * This makes biometric optional but required for enrolled users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BioSSOAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final UserBiometricMetadataRepository biometricMetadataRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = authentication.getName();
        String password = (String) authentication.getCredentials();

        log.debug("🔐 Authenticating user: {}", email);

        // Step 1: Find user
        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.warn("❌ User not found: {}", email);
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userOpt.get();

        // Step 2: Verify password
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("❌ Password mismatch for user: {}", email);
            throw new BadCredentialsException("Invalid email or password");
        }

        log.info("✅ Password verified for user: {}", email);

        // Step 3: Check biometric enrollment status
        var biometricOpt = biometricMetadataRepository.findByUser(user);

        if (biometricOpt.isPresent()) {
            UserBiometricMetadata metadata = biometricOpt.get();
            
            // If biometric is ACTIVE, require biometric verification
            if (metadata.getStatus() == UserBiometricMetadata.BiometricStatus.ACTIVE) {
                log.info("👤 User {} requires biometric verification", email);
                
                // Return partial authentication token - NOT FULLY AUTHENTICATED
                // Frontend will capture images and call /api/biometric/verify
                return new BiometricPendingAuthenticationToken(email);
            } else {
                log.info("⚠️  User {} has biometric enrollment but status is: {}", email, metadata.getStatus());
            }
        } else {
            log.info("ℹ️  User {} has no biometric enrollment - proceeding with password auth alone", email);
        }

        // Step 4: No biometric required (or not enrolled)
        // Return full authentication token - PROCEED TO OAUTH2 FLOW
        log.info("✅ User successfully authenticated: {} (full auth)", email);
        return new UsernamePasswordAuthenticationToken(
                email,
                null,  // Don't store password in token
                user.getAuthorities()  // Include user roles
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
