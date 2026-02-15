package com.fijalkoa.biosso.repository;

import com.fijalkoa.biosso.model.BiometricSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BiometricSessionRepository extends JpaRepository<BiometricSession, Long> {
    Optional<BiometricSession> findBySessionId(String sessionId);
    Optional<BiometricSession> findByPythonMicroserviceCorrelationId(String correlationId);
}
