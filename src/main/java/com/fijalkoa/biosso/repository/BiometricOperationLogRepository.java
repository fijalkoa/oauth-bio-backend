package com.fijalkoa.biosso.repository;

import com.fijalkoa.biosso.model.BiometricOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BiometricOperationLogRepository extends JpaRepository<BiometricOperationLog, Long> {
    List<BiometricOperationLog> findBySessionId(String sessionId);
}
