package com.printops.demo.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Adaptador de produccion: verifica firma, emisor, expiracion y audience con la
// libreria oficial de Google (cachea las claves publicas de Google).
@Component
public class GoogleApiTokenVerifier implements GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleApiTokenVerifier.class);

    @Value("${google.client-ids:}")
    private String clientIdsProperty;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        List<String> clientIds = Arrays.stream(clientIdsProperty == null ? new String[0] : clientIdsProperty.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (clientIds.isEmpty()) {
            log.warn("Google Sign-In NO configurado (GOOGLE_CLIENT_IDS vacía): /api/auth/google responde 503.");
            return;
        }
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(clientIds)
                .build();
        log.info("Google Sign-In configurado con {} client ID(s).", clientIds.size());
    }

    @Override
    public boolean isConfigured() {
        return verifier != null;
    }

    @Override
    public Optional<GoogleIdentity> verify(String idToken) {
        if (verifier == null) return Optional.empty();
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) return Optional.empty();
            GoogleIdToken.Payload p = token.getPayload();
            return Optional.of(new GoogleIdentity(
                    p.getSubject(),
                    p.getEmail(),
                    Boolean.TRUE.equals(p.getEmailVerified()),
                    (String) p.get("name")));
        } catch (Exception e) {
            // Token mal formado, o no se pudieron obtener las claves de Google.
            log.warn("No se pudo verificar el ID token de Google: {}", e.toString());
            return Optional.empty();
        }
    }
}
