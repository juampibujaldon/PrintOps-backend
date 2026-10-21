package com.printops.demo.service;

import java.util.Optional;

// Puerto hacia Google: los tests lo reemplazan por un falso.
public interface GoogleTokenVerifier {

    boolean isConfigured();

    // Vacio si el token es invalido, expiro, o no fue emitido para nuestros client IDs.
    Optional<GoogleIdentity> verify(String idToken);
}
