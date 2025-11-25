package com.fijalkoa.biosso.controller;

import com.fijalkoa.biosso.util.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        var pub = rsaKeyProvider.getPublicKey();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "alg", "RS256",
                "use", "sig",
                "kid", "1",
                "n", Base64.getUrlEncoder().encodeToString(pub.getModulus().toByteArray()),
                "e", Base64.getUrlEncoder().encodeToString(pub.getPublicExponent().toByteArray())
        )));
    }
}

