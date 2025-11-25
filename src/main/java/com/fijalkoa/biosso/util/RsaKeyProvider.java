package com.fijalkoa.biosso.util;

import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;

@Component
public class RsaKeyProvider {
    private final KeyPair keyPair;

    public RsaKeyProvider() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            keyPair = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public PrivateKey getPrivateKey() { return keyPair.getPrivate(); }
    public RSAPublicKey getPublicKey() { return (RSAPublicKey) keyPair.getPublic(); }
}

