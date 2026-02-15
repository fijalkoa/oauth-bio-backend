package com.fijalkoa.biosso.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "biometric_operations_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricOperationLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BiometricOperation operation;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OperationResult result;

    @Column
    private BigDecimal confidenceScore;

    @Column
    private BigDecimal fraudScore;

    @Column
    private Integer pythonResponseTimeMs;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private String sessionId;

    @Column(columnDefinition = "JSONB")
    private String details;

    public enum BiometricOperation {
        ENROLLMENT, VERIFICATION, LIVENESS_CHECK, FRAUD_DETECTION
    }

    public enum OperationResult {
        SUCCESS, FAILED, SUSPICIOUS, ERROR
    }
}
