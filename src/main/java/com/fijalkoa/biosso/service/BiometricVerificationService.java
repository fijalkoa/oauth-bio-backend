package com.fijalkoa.biosso.service;

import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.model.UserBiometricMetadata;
import com.fijalkoa.biosso.repository.BiometricOperationLogRepository;
import com.fijalkoa.biosso.repository.UserBiometricMetadataRepository;
import com.fijalkoa.biosso.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Biometric Verification Service
 * 
 * Handles:
 * 1. Verification with Python microservice (face recognition)
 * 2. Embedding matching against enrolled data
 * 3. Authentication token upgrade (from BIOMETRIC_PENDING to FULL)
 * 4. Audit logging
 * 
 * After successful biometric verification, user is considered fully authenticated
 * and can proceed to OAuth2/OIDC token acquisition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricVerificationService {

    private final BiometricRestService biometricRestService;
    private final UserRepository userRepository;
    private final UserBiometricMetadataRepository biometricMetadataRepository;
    private final BiometricOperationLogRepository operationLogRepository;

    /**
     * Verify biometric image with enrolled templates
     * 
     * Called after frontend captures image during login
     * If verification succeeds, completes the authentication flow
     * Then user can proceed to OAuth2 authorization
     */
    public Map<String, Object> verifyAndAuthenticate(String userEmail, MultipartFile image, double threshold) {
        log.info("🔐 Starting biometric verification for user: {}", userEmail);

        // Find user
        var userOpt = userRepository.findByEmail(userEmail);
        if (userOpt.isEmpty()) {
            log.warn("❌ User not found during biometric verify: {}", userEmail);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        User user = userOpt.get();

        // Check if user has biometric enrollment
        var biometricOpt = biometricMetadataRepository.findByUser(user);
        if (biometricOpt.isEmpty()) {
            log.warn("❌ User {} has no biometric enrollment", userEmail);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User has no biometric enrollment");
        }

        UserBiometricMetadata metadata = biometricOpt.get();
        if (metadata.getStatus() != UserBiometricMetadata.BiometricStatus.ACTIVE) {
            log.warn("❌ User {} biometric status is not ACTIVE: {}", userEmail, metadata.getStatus());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Biometric enrollment not active");
        }

        // Call Python microservice for verification
        Map<String, Object> verifyResult;
        try {
            verifyResult = biometricRestService.verifyUser(user.getId(), threshold, image);
            log.debug("📊 Verification result: {}", verifyResult);
        } catch (Exception e) {
            log.error("❌ Biometric verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Biometric verification service error");
        }

        // Check verification result
        boolean isMatched = (boolean) verifyResult.getOrDefault("is_matched", false);
        double confidence = ((Number) verifyResult.getOrDefault("confidence", 0)).doubleValue();

        if (!isMatched) {
            log.warn("❌ Biometric verification failed for user: {} (confidence: {})", userEmail, confidence);
            // TODO: Log failed attempt, potentially lock account after N failures
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Face verification failed");
        }

        log.info("✅ Biometric verification successful for user: {} (confidence: {}%)", userEmail, confidence);

        // Step 3: Upgrade authentication - mark user as fully authenticated
        upgradeAuthentication(user);

        // Log successful biometric verification
        // TODO: Create BiometricOperationLog entry

        return Map.of(
                "status", "verified",
                "message", "Biometric verification successful",
                "user_email", userEmail,
                "confidence", confidence
        );
    }

    /**
     * Upgrade authentication from BIOMETRIC_PENDING to FULL
     * 
     * After successful biometric verification, this creates a full authentication token
     * and updates the SecurityContext so user can proceed to OAuth2 authorization endpoint.
     */
    private void upgradeAuthentication(User user) {
        log.info("⬆️  Upgrading authentication for user: {}", user.getEmail());

        // Create full authentication token
        Authentication fullAuth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,  // Password not needed anymore
                user.getAuthorities()
        );

        // Update security context
        SecurityContextHolder.getContext().setAuthentication(fullAuth);

        log.info("✅ Authentication upgraded for user: {} - ready for OAuth2 flow", user.getEmail());
    }
}
