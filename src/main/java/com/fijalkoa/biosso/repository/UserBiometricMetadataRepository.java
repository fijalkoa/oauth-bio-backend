package com.fijalkoa.biosso.repository;

import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.model.UserBiometricMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserBiometricMetadataRepository extends JpaRepository<UserBiometricMetadata, Long> {
    Optional<UserBiometricMetadata> findByUser(User user);
}
