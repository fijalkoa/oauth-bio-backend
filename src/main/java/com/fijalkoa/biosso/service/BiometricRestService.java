package com.fijalkoa.biosso.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Biometric REST Service - Proxy layer for Python Microservice
 * 
 * Forwards multipart form data (images + metadata) to Python biometric service
 * All encryption, face recognition, and embeddings are handled by Python
 * Java only:
 * - Validates input
 * - Forwards requests to Python
 * - Maps responses and stores metadata in DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricRestService {

    @Value("${app.biometric.microservice-url:http://localhost:5001}")
    private String microserviceUrl;

    private final RestTemplate restTemplate;

    /**
     * Register user with multiple face images
     * Forwards multipart request to Python microservice
     */
    public ResponseEntity<Map<String, Object>> registerUser(
            String userId,
            MultipartFile imageFront,
            MultipartFile imageLeft,
            MultipartFile imageRight,
            MultipartFile imageUp,
            MultipartFile imageDown,
            boolean checkLiveness) {

        try {
            log.info("📤 Forwarding registration request to Python: user={}", userId);

            // Build multipart form for Python
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("user_id", userId);
            body.add("check_liveness", String.valueOf(checkLiveness));
            
            // Add all 5 images
            body.add("image_front", new ByteArrayResource(imageFront.getBytes()) {
                @Override
                public String getFilename() {
                    return "front.jpg";
                }
            });
            
            body.add("image_left", new ByteArrayResource(imageLeft.getBytes()) {
                @Override
                public String getFilename() {
                    return "left.jpg";
                }
            });
            
            body.add("image_right", new ByteArrayResource(imageRight.getBytes()) {
                @Override
                public String getFilename() {
                    return "right.jpg";
                }
            });
            
            body.add("image_up", new ByteArrayResource(imageUp.getBytes()) {
                @Override
                public String getFilename() {
                    return "up.jpg";
                }
            });
            
            body.add("image_down", new ByteArrayResource(imageDown.getBytes()) {
                @Override
                public String getFilename() {
                    return "down.jpg";
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            long startTime = System.currentTimeMillis();
            
            ResponseEntity<Map> pythonResponse = restTemplate.postForEntity(
                    microserviceUrl + "/api/biometrics/register",
                    request,
                    Map.class
            );

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("✅ Python response received for registration: status={}, time={}ms", 
                pythonResponse.getStatusCodeValue(), processingTime);

            // Cast response to proper type
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> typedResponse = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) pythonResponse;
            
            return typedResponse;

        } catch (IOException e) {
            log.error("❌ Error reading image files: {}", e.getMessage(), e);
            return ResponseEntity.status(400)
                .body(Map.of(
                    "success", false,
                    "error", "Failed to read image files: " + e.getMessage()
                ));
        } catch (Exception e) {
            log.error("❌ Error forwarding registration to Python: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", "Failed to forward request to biometric service: " + e.getMessage()
                ));
        }
    }

    /**
     * Verify user with single face image
     * Forwards multipart request to Python microservice
     */
    public ResponseEntity<Map<String, Object>> verifyUser(
            String userId,
            MultipartFile image,
            double threshold,
            boolean checkLiveness) {

        try {
            log.info("📤 Forwarding verification request to Python: user={}, threshold={}", userId, threshold);

            // Build multipart form for Python
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("user_id", userId);
            body.add("threshold", String.valueOf(threshold));
            body.add("check_liveness", String.valueOf(checkLiveness));
            
            body.add("image", new ByteArrayResource(image.getBytes()) {
                @Override
                public String getFilename() {
                    return "face.jpg";
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            long startTime = System.currentTimeMillis();
            
            ResponseEntity<Map> pythonResponse = restTemplate.postForEntity(
                    microserviceUrl + "/api/biometrics/verify",
                    request,
                    Map.class
            );

            long processingTime = System.currentTimeMillis() - startTime;
            log.info("✅ Python response received for verification: status={}, time={}ms", 
                pythonResponse.getStatusCodeValue(), processingTime);

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> typedResponse = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) pythonResponse;
            
            return typedResponse;

        } catch (IOException e) {
            log.error("❌ Error reading image file: {}", e.getMessage(), e);
            return ResponseEntity.status(400)
                .body(Map.of(
                    "success", false,
                    "error", "Failed to read image file: " + e.getMessage()
                ));
        } catch (Exception e) {
            log.error("❌ Error forwarding verification to Python: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(Map.of(
                    "success", false,
                    "error", "Failed to forward request to biometric service: " + e.getMessage()
                ));
        }
    }

    /**
     * Health check for biometric microservice
     */
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            log.debug("🔍 Checking Python biometric service health...");
            
            ResponseEntity<Map> pythonResponse = restTemplate.getForEntity(
                    microserviceUrl + "/api/biometrics/health",
                    Map.class
            );

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> typedResponse = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) pythonResponse;
            
            log.info("✅ Python service is healthy");
            return typedResponse;

        } catch (Exception e) {
            log.warn("⚠️ Python biometric service is unavailable: {}", e.getMessage());
            return ResponseEntity.status(503)
                .body(Map.of(
                    "status", "unavailable",
                    "error", "Biometric microservice is not responding"
                ));
        }
    }

    /**
     * Get metrics from Python microservice
     */
    public ResponseEntity<Map<String, Object>> getMetrics() {
        try {
            log.debug("📊 Fetching metrics from Python service...");
            
            ResponseEntity<Map> pythonResponse = restTemplate.getForEntity(
                    microserviceUrl + "/api/biometrics/metrics",
                    Map.class
            );

            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> typedResponse = 
                (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) pythonResponse;
            
            return typedResponse;

        } catch (Exception e) {
            log.error("❌ Error fetching metrics from Python: {}", e.getMessage());
            return ResponseEntity.status(500)
                .body(Map.of(
                    "error", "Failed to fetch metrics: " + e.getMessage()
                ));
        }
    }
}
