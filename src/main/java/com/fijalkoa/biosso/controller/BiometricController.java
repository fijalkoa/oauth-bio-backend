package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.service.BiometricRestService;
import com.fijalkoa.biosso.service.BiometricVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Biometric REST Controller - OAuth2/OIDC Integration
 * 
 * Handles:
 * 1. Biometric user registration (5 face images)
 * 2. Biometric user verification (1 face image)
 * 3. Biometric authentication (verification + OIDC token grant)
 * 
 * Endpoints:
 * - POST /api/biometric/register - Create biometric enrollment
 * - POST /api/biometric/verify - Standalone biometric verification
 * - POST /api/biometric/verify-for-auth - During login (verifies + completes OAuth2 flow)
 * - GET  /api/biometric/health - Health check
 * - GET  /api/biometric/metrics - Performance metrics
 * 
 * The /verify-for-auth endpoint is called after:
 * 1. User submits email+password (/doLogin)
 * 2. BioSSOAuthenticationProvider checks if biometric is required
 * 3. If yes, returns BiometricPendingAuthenticationToken
 * 4. Frontend captures image and posts to /api/biometric/verify-for-auth
 * 5. Java verifies with Python microservice
 * 6. If match, upgrades authentication to FULL
 * 7. Frontend redirects to /oauth2/authorize (receives auth code)
 */
@Slf4j
@RestController
@RequestMapping("/api/biometric")
@RequiredArgsConstructor
public class BiometricController {

    private final BiometricRestService biometricRestService;
    private final BiometricVerificationService biometricVerificationService;

    /**
     * Register user with 5 face images
     * 
     * Required: user_id, image_front, image_left, image_right, image_up, image_down
     * Optional: check_liveness (default: true)
     * 
     * Response: enrollment_id, template_hash, status
     */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> registerUser(
            @RequestParam("user_id") String userId,
            @RequestParam("image_front") MultipartFile imageFront,
            @RequestParam("image_left") MultipartFile imageLeft,
            @RequestParam("image_right") MultipartFile imageRight,
            @RequestParam("image_up") MultipartFile imageUp,
            @RequestParam("image_down") MultipartFile imageDown,
            @RequestParam(value = "check_liveness", defaultValue = "true") boolean checkLiveness) {

        log.info("📝 Biometric registration request: user={}", userId);

        return biometricRestService.registerUser(
                userId,
                imageFront,
                imageLeft,
                imageRight,
                imageUp,
                imageDown,
                checkLiveness
        );
    }

    /**
     * Verify user with a single face image
     * (Standalone verification - not tied to authentication)
     * 
     * Required: user_id, image
     * Optional: threshold (default: 0.5), check_liveness (default: true)
     * 
     * Response: is_matched, confidence, verification_time_ms
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> verifyUser(
            @RequestParam("user_id") String userId,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "threshold", defaultValue = "0.5") double threshold,
            @RequestParam(value = "check_liveness", defaultValue = "true") boolean checkLiveness) {

        log.info("🔍 Standalone biometric verification: user={}, threshold={}", userId, threshold);

        return biometricRestService.verifyUser(
                userId,
                image,
                threshold,
                checkLiveness
        );
    }

    /**
     * Verify user for authentication - OIDC flow integration
     * 
     * Called during login after BioSSOAuthenticationProvider requires biometric verification.
     * 
     * Required: user_email, image
     * Optional: threshold (default: 0.5)
     * 
     * Process:
     * 1. Call Python microservice to verify face vs enrolled templates
     * 2. Check if embeddings match
     * 3. If match:
     *    - Upgrade authentication to FULL
     *    - Clear session flags
     *    - Return success with redirect URL to /oauth2/authorize
     * 4. If no match:
     *    - Return 403 Forbidden
     * 
     * Response: status, message, user_email, confidence, redirect_url (optional)
     */
    @PostMapping(value = "/verify-for-auth", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> verifyForAuthentication(
            @RequestParam("user_email") String userEmail,
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "threshold", defaultValue = "0.5") double threshold) {

        log.info("🔐 Biometric verification for authentication: user={}, threshold={}", userEmail, threshold);

        // This calls BiometricVerificationService which:
        // 1. Verifies with Python microservice
        // 2. Checks embeddings match enrolled data  
        // 3. Upgrades authentication token
        // 4. Updates SecurityContext
        Map<String, Object> result = biometricVerificationService.verifyAndAuthenticate(userEmail, image, threshold);

        // Add redirect URL for frontend to know where to go next
        result.put("redirect_url", "/oauth2/authorize");

        return ResponseEntity.ok(result);
    }

    /**
     * Health check - verify biometric microservice is running
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("🏥 Biometric service health check");
        return biometricRestService.healthCheck();
    }

    /**
     * Get performance metrics from biometric microservice
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        log.debug("📊 Biometric service metrics requested");
        return biometricRestService.getMetrics();
    }
}

