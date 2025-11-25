package com.fijalkoa.biosso.service;

import com.fijalkoa.biosso.model.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthorizationCodeService {

    private final Map<String, CodeData> codes = new ConcurrentHashMap<>();

    public String generateCode(User user, String clientId, String redirectUri,
                               String codeChallenge, String codeChallengeMethod, String nonce) {
        String code = UUID.randomUUID().toString();
        codes.put(code, new CodeData(
                user, clientId, redirectUri,
                codeChallenge, codeChallengeMethod,
                Instant.now().plusSeconds(120), nonce
        ));
        return code;
    }
    public String generateCode(User user, String clientId, String redirectUri) {
        String code = UUID.randomUUID().toString();
        codes.put(code, new CodeData(
                user, clientId, redirectUri,
                null, null,
                Instant.now().plusSeconds(120), null
        ));
        return code;
    }

    public CodeData consumeCode(String code, String clientId, String redirectUri) {
        CodeData data = codes.remove(code);
        if (data == null || data.expires().isBefore(Instant.now())) return null;
        if (!data.clientId().equals(clientId) || !data.redirectUri().equals(redirectUri)) return null;
        return data;
    }

    public record CodeData(
            User user,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            Instant expires,
            String nonce
    ) {}
}

