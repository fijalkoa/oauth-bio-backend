package com.fijalkoa.biosso.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "biometric_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BiometricSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private String sessionId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BiometricMode mode; // login, register

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BiometricStatus status; // active, completed, failed, abandoned

    @Column(nullable = false)
    private Integer imagesReceived = 0;

    @Column
    private String pythonMicroserviceCorrelationId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime completedAt;

    @Column
    private String clientIp;

    @Column(length = 1000)
    private String userAgent;

    public enum BiometricMode {
        LOGIN, REGISTER
    }

    public enum BiometricStatus {
        ACTIVE, COMPLETED, FAILED, ABANDONED
    }
}
