package com.fijalkoa.biosso;

import com.fijalkoa.biosso.model.User;
import com.fijalkoa.biosso.model.UserBiometricMetadata;
import com.fijalkoa.biosso.repository.UserBiometricMetadataRepository;
import com.fijalkoa.biosso.repository.UserRepository;
import com.fijalkoa.biosso.security.BiometricPendingAuthenticationToken;
import com.fijalkoa.biosso.security.BioSSOAuthenticationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BioSSOAuthenticationProvider – testy jednostkowe")
class BioSSOAuthenticationProviderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBiometricMetadataRepository biometricMetadataRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private BioSSOAuthenticationProvider provider;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setEmail("test@example.com");
        testUser.setPassword("$2a$10$hashed_password");
    }

    @Test
    @DisplayName("Nieistniejący użytkownik → BadCredentialsException")
    void whenUserNotFound_thenThrowsBadCredentials() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Authentication input = new UsernamePasswordAuthenticationToken(
                "unknown@example.com", "anyPassword");

        assertThatThrownBy(() -> provider.authenticate(input))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Błędne hasło → BadCredentialsException")
    void whenPasswordWrong_thenThrowsBadCredentials() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", testUser.getPassword())).thenReturn(false);

        Authentication input = new UsernamePasswordAuthenticationToken(
                "test@example.com", "wrongPassword");

        assertThatThrownBy(() -> provider.authenticate(input))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("Poprawne hasło + brak biometrii → UsernamePasswordAuthenticationToken (fully authenticated)")
    void whenPasswordCorrectAndNoBiometrics_thenReturnsFullToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", testUser.getPassword())).thenReturn(true);
        when(biometricMetadataRepository.findByUser(testUser)).thenReturn(Optional.empty());

        Authentication input = new UsernamePasswordAuthenticationToken(
                "test@example.com", "correctPassword");

        Authentication result = provider.authenticate(input);

        assertThat(result).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("Poprawne hasło + ACTIVE biometria → BiometricPendingAuthenticationToken")
    void whenPasswordCorrectAndBiometricsActive_thenReturnsPendingToken() {
        UserBiometricMetadata metadata = UserBiometricMetadata.builder()
                .user(testUser)
                .status(UserBiometricMetadata.BiometricStatus.ACTIVE)
                .enrolledAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", testUser.getPassword())).thenReturn(true);
        when(biometricMetadataRepository.findByUser(testUser)).thenReturn(Optional.of(metadata));

        Authentication input = new UsernamePasswordAuthenticationToken(
                "test@example.com", "correctPassword");

        Authentication result = provider.authenticate(input);

        assertThat(result).isInstanceOf(BiometricPendingAuthenticationToken.class);
        assertThat(result.isAuthenticated()).isFalse();
        assertThat(result.getName()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Poprawne hasło + REVOKED biometria → pełny token (biometria ignorowana)")
    void whenBiometricsRevoked_thenReturnsFullToken() {
        UserBiometricMetadata metadata = UserBiometricMetadata.builder()
                .user(testUser)
                .status(UserBiometricMetadata.BiometricStatus.REVOKED)
                .enrolledAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("correctPassword", testUser.getPassword())).thenReturn(true);
        when(biometricMetadataRepository.findByUser(testUser)).thenReturn(Optional.of(metadata));

        Authentication input = new UsernamePasswordAuthenticationToken(
                "test@example.com", "correctPassword");

        Authentication result = provider.authenticate(input);

        assertThat(result).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("supports() zwraca true dla UsernamePasswordAuthenticationToken")
    void supportsUsernamePasswordToken() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("supports() zwraca false dla BiometricPendingAuthenticationToken")
    void doesNotSupportBiometricPendingToken() {
        assertThat(provider.supports(BiometricPendingAuthenticationToken.class)).isFalse();
    }
}
