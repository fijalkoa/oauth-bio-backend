package com.fijalkoa.biosso.service;

import com.fijalkoa.biosso.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientService {
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean validateClient(String clientId, String redirectUri) {
        return clientRepository.findByClientId(clientId)
                .map(client -> client.getRedirectUris().contains(redirectUri))
                .orElse(false);
    }

    public boolean validateClientCredentials(String clientId, String clientSecret) {
        return clientRepository.findByClientId(clientId)
                .map(client -> passwordEncoder.matches(clientSecret, client.getClientSecret()))
                .orElse(false);
    }
}
