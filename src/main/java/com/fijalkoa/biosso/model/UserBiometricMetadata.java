package com.fijalkoa.biosso.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_biometric_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBiometricMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private LocalDateTime enrolledAt;

    @Column
    private LocalDateTime lastUpdated = LocalDateTime.now();

    @Column
    private String templateId; // Reference to Python microservice template ID

    @Column
    private BigDecimal livenessThreshold = new BigDecimal("0.95");

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BiometricStatus status = BiometricStatus.ACTIVE;

    @Column(length = 1000)
    private String notes;

    public enum BiometricStatus {
        ACTIVE, INACTIVE, REVOKED
    }
}
