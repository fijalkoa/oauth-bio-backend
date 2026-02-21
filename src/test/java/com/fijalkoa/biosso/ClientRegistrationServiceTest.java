package com.fijalkoa.biosso;

import com.fijalkoa.biosso.service.ClientRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientRegistrationService – testy walidacji DCR")
class ClientRegistrationServiceTest {

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    private ClientRegistrationService service;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        service = new ClientRegistrationService(encoder, registeredClientRepository);
        ReflectionTestUtils.setField(service, "issuer", "http://localhost:8080");
        doNothing().when(registeredClientRepository).save(any());
    }


    @Test
    @DisplayName("HTTPS redirect URI → rejestracja poprawna")
    void validHttpsRedirectUri_registrationSucceeds() {
        Map<String, Object> request = Map.of(
                "client_name", "TestApp",
                "redirect_uris", List.of("https://myapp.example.com/auth/callback")
        );

        Map<String, Object> response = service.registerClient(request);

        assertThat(response).containsKey("client_id");
        assertThat(response).containsKey("client_secret");
        assertThat((String) response.get("client_id")).startsWith("client_");
    }

    @Test
    @DisplayName("HTTP localhost redirect URI → rejestracja poprawna")
    void httpLocalhostRedirectUri_registrationSucceeds() {
        Map<String, Object> request = Map.of(
                "client_name", "DevApp",
                "redirect_uris", List.of("http://localhost:3000/oauth/callback")
        );

        assertThatNoException().isThrownBy(() -> service.registerClient(request));
    }

    @Test
    @DisplayName("HTTP redirect URI (nie localhost) → IllegalArgumentException")
    void httpNonLocalhostRedirectUri_throwsException() {
        Map<String, Object> request = Map.of(
                "client_name", "InsecureApp",
                "redirect_uris", List.of("http://myapp.example.com/callback")
        );

        assertThatThrownBy(() -> service.registerClient(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid redirect URI");
    }

    @Test
    @DisplayName("Redirect URI z fragmentem (#) → IllegalArgumentException")
    void redirectUriWithFragment_throwsException() {
        Map<String, Object> request = Map.of(
                "client_name", "FragmentApp",
                "redirect_uris", List.of("https://myapp.com/callback#section")
        );

        assertThatThrownBy(() -> service.registerClient(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Redirect URI z wildcardem (*) → IllegalArgumentException")
    void redirectUriWithWildcard_throwsException() {
        Map<String, Object> request = Map.of(
                "client_name", "WildApp",
                "redirect_uris", List.of("https://*.example.com/callback")
        );

        assertThatThrownBy(() -> service.registerClient(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Brak client_name → IllegalArgumentException")
    void missingClientName_throwsException() {
        Map<String, Object> request = Map.of(
                "redirect_uris", List.of("https://myapp.com/callback")
        );

        assertThatThrownBy(() -> service.registerClient(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("client_name");
    }

    @Test
    @DisplayName("Pusta lista redirect_uris → IllegalArgumentException")
    void emptyRedirectUris_throwsException() {
        Map<String, Object> request = Map.of(
                "client_name", "EmptyUriApp",
                "redirect_uris", List.of()
        );

        assertThatThrownBy(() -> service.registerClient(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("redirect_uris");
    }

    @Test
    @DisplayName("Poprawna rejestracja → odpowiedź zawiera wymagane pola OIDC DCR")
    void validRegistration_responseContainsOidcFields() {
        Map<String, Object> request = Map.of(
                "client_name", "FullApp",
                "redirect_uris", List.of("https://full-app.example.com/callback")
        );

        Map<String, Object> response = service.registerClient(request);

        assertThat(response).containsKeys(
                "client_id",
                "client_secret",
                "client_name",
                "redirect_uris",
                "grant_types",
                "response_types",
                "scope",
                "client_id_issued_at",
                "client_secret_expires_at"
        );
        assertThat(response.get("client_secret_expires_at")).isEqualTo(0);
        assertThat(response.get("require_pkce")).isEqualTo(true);
    }

    @Test
    @DisplayName("Każda rejestracja generuje unikalny client_id")
    void eachRegistration_generatesUniqueClientId() {
        Map<String, Object> request = Map.of(
                "client_name", "App",
                "redirect_uris", List.of("https://app.example.com/callback")
        );

        String id1 = (String) service.registerClient(request).get("client_id");
        String id2 = (String) service.registerClient(request).get("client_id");

        assertThat(id1).isNotEqualTo(id2);
    }
}
