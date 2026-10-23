package com.printops.demo.controller;

import com.printops.demo.dto.AuthResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

// Cookie httpOnly del refresh token: JavaScript no la puede leer (un XSS no se lleva la
// sesión) y solo viaja a /api/auth. SameSite=Strict: el front y la API comparten origen
// gracias al proxy de nginx, así que un sitio de terceros nunca la envía (CSRF).
@Component
public class RefreshCookies {

    static final String PATH = "/api/auth";

    private final String name;
    private final boolean secure;

    public RefreshCookies(@Value("${app.auth.refresh-cookie.name:printops_rt}") String name,
                          @Value("${app.auth.refresh-cookie.secure:true}") boolean secure) {
        this.name = name;
        this.secure = secure;
    }

    public String issue(AuthResponse auth) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, auth.refreshToken())
                .httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
        if (auth.persistentRefresh() && auth.refreshExpiresAt() != null) {
            // "Recordarme": persiste hasta que vence el token. Sin eso, cookie de sesión.
            b.maxAge(Duration.between(Instant.now(), auth.refreshExpiresAt()).toSeconds());
        }
        return b.build().toString();
    }

    public String clear() {
        return ResponseCookie.from(name, "").httpOnly(true).secure(secure).sameSite("Strict")
                .path(PATH).maxAge(0).build().toString();
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }
}
