package com.fijalkoa.biosso.util;

import com.fijalkoa.biosso.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Date;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final RsaKeyProvider keyProvider;

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .setSubject(user.getId().toString())
                .claim("email", user.getEmail())
                .setIssuer("http://localhost:8080")
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    public String generateIdToken(User user, String clientId, String nonce) {
        return Jwts.builder()
                .setHeaderParam("kid", "1") // 🔑 DODAJ TO — musi zgadzać się z JWKS
                .setIssuer("http://localhost:8080")
                .setSubject(user.getId().toString())
                .setAudience(clientId)
                .claim("email", user.getEmail())
                .claim("nonce", nonce)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    public String extractEmail(String token) {
        var claims = Jwts.parserBuilder()
                .setSigningKey(keyProvider.getPublicKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("email", String.class);
    }
}
