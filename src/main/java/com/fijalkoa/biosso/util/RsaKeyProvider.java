package com.fijalkoa.biosso.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
public class RsaKeyProvider {
    private final KeyPair keyPair;
    private static final String DEFAULT_KEYSTORE_PATH = "keys/rsa-keystore";
    private static final String PRIVATE_KEY_FILE = "private_key.der";
    private static final String PUBLIC_KEY_FILE = "public_key.der";

    public RsaKeyProvider(@Value("${app.security.keystore-path:" + DEFAULT_KEYSTORE_PATH + "}") String keystorePath) {
        try {
            keyPair = loadOrGenerateKeyPair(keystorePath);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize RSA key pair", e);
        }
    }

    private KeyPair loadOrGenerateKeyPair(String keystorePath) throws Exception {
        File privatKeyFile = new File(keystorePath, PRIVATE_KEY_FILE);
        File publicKeyFile = new File(keystorePath, PUBLIC_KEY_FILE);

        // Jeśli klucze istnieją, wczytaj je
        if (privatKeyFile.exists() && publicKeyFile.exists()) {
            System.out.println("✅ Loading RSA keys from: " + keystorePath);
            return loadKeyPair(keystorePath);
        }

        // W przeciwnym razie, wygeneruj nowe
        System.out.println("🔑 Generating new RSA key pair...");
        KeyPair newKeyPair = generateNewKeyPair();

        // Zapisz klucze na dysk
        saveKeyPair(newKeyPair, keystorePath);
        System.out.println("✅ RSA keys saved to: " + keystorePath);

        return newKeyPair;
    }

    private KeyPair loadKeyPair(String keystorePath) throws Exception {
        // Wczytaj private key
        byte[] privateKeyBytes = Files.readAllBytes(Paths.get(keystorePath, PRIVATE_KEY_FILE));
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        // Wczytaj public key
        byte[] publicKeyBytes = Files.readAllBytes(Paths.get(keystorePath, PUBLIC_KEY_FILE));
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    private KeyPair generateNewKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    private void saveKeyPair(KeyPair keyPair, String keystorePath) throws Exception {
        File keystoreDir = new File(keystorePath);
        if (!keystoreDir.exists()) {
            keystoreDir.mkdirs();
        }

        // Zapisz private key
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();
        Files.write(Paths.get(keystorePath, PRIVATE_KEY_FILE), privateKeyBytes);

        // Zapisz public key
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        Files.write(Paths.get(keystorePath, PUBLIC_KEY_FILE), publicKeyBytes);
    }

    public PrivateKey getPrivateKey() { 
        return keyPair.getPrivate(); 
    }

    public RSAPublicKey getPublicKey() { 
        return (RSAPublicKey) keyPair.getPublic(); 
    }
}

