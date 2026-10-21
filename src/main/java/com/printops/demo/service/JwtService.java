// src/main/java/com/printops/demo/service/JwtService.java
package com.printops.demo.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final String PURPOSE_CLAIM = "purpose";
    private static final String ONBOARDING_PURPOSE = "google_onboarding";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // Falla al arrancar con un mensaje claro si la clave no sirve. Antes el backend
    // levantaba igual y recién explotaba al firmar el token en cada login válido.
    @PostConstruct
    void validateSecret() {
        try {
            getSigningKey();
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET inválida: tiene que ser Base64 de al menos 32 bytes (256 bits). "
                    + "Generala con `openssl rand -base64 32`, o en desarrollo borrá/comentá JWT_SECRET del .env "
                    + "para usar la de application-dev.yaml. Detalle: " + e.getMessage(), e);
        }
    }

    public String generateAccessToken(String email, String role, Long workspaceId) {
        return Jwts.builder()
                // jti unico: sin el, dos tokens del mismo usuario en el mismo segundo son
                // identicos y el blacklist del logout invalidaria tambien al nuevo.
                .id(java.util.UUID.randomUUID().toString())
                .subject(email)
                .claim("role", role)
                .claim("workspaceId", workspaceId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Token de alta con Google: vive poco y lleva un claim "purpose" que lo distingue
    // de un access token (ver validateToken y parseOnboardingToken).
    public record OnboardingClaims(String googleSubject, String email, String name) {}

    public String generateOnboardingToken(String googleSubject, String email, String name, long ttlMs) {
        return Jwts.builder()
                .subject(googleSubject)
                .claim(PURPOSE_CLAIM, ONBOARDING_PURPOSE)
                .claim("email", email)
                .claim("name", name)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(getSigningKey())
                .compact();
    }

    public Optional<OnboardingClaims> parseOnboardingToken(String token) {
        try {
            Claims c = getClaims(token);
            if (!ONBOARDING_PURPOSE.equals(c.get(PURPOSE_CLAIM, String.class))) return Optional.empty();
            return Optional.of(new OnboardingClaims(c.getSubject(), c.get("email", String.class), c.get("name", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // Solo access tokens: un token con "purpose" (p. ej. de onboarding) nunca autentica.
    public boolean validateToken(String token) {
        try {
            Claims c = getClaims(token);
            if (c.get(PURPOSE_CLAIM) != null) return false;
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    // FIX 3: workspaceId incluido en el JWT al momento del login.
    public Long extractWorkspaceId(String token) {
        Object ws = getClaims(token).get("workspaceId");
        return ws != null ? ((Number) ws).longValue() : null;
    }

    public Date extractExpiration(String token) {
        return getClaims(token).getExpiration();
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
