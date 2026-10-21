// src/main/java/com/printops/demo/controller/AuthController.java
package com.printops.demo.controller;

import com.printops.demo.dto.*;
import com.printops.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        String ip = obtenerIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        AuthResponse response = authService.login(
                request.email(), request.password(), request.deviceId(),
                request.rememberMe(), ip, userAgent);
        return ResponseEntity.ok(response);
    }

    // 200 AuthResponse (existente/vinculada) o 202 con el paso de onboarding (sin cuenta).
    @PostMapping("/google")
    public ResponseEntity<?> google(@Valid @RequestBody GoogleLoginRequest request,
                                    HttpServletRequest httpRequest) {
        GoogleLoginResult result = authService.loginWithGoogle(
                request.idToken(), request.deviceId(), request.rememberMe(),
                obtenerIp(httpRequest), httpRequest.getHeader("User-Agent"));
        if (result.auth() != null) {
            return ResponseEntity.ok(result.auth());
        }
        return ResponseEntity.accepted().body(result.onboarding());
    }

    @PostMapping("/google/complete")
    public ResponseEntity<AuthResponse> googleComplete(@Valid @RequestBody GoogleCompleteRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.completeGoogleSignup(
                request.onboardingToken(), request.deviceId(), request.rememberMe(),
                request.inviteToken(), request.workspaceName(),
                obtenerIp(httpRequest), httpRequest.getHeader("User-Agent")));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request.email(), request.password(), request.inviteToken(), request.workspaceName());
        return ResponseEntity.ok(Map.of(
                "message", "Usuario registrado correctamente."
        ));
    }

    // Verificación por mail con código de 6 dígitos ingresado por el usuario.
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        try {
            authService.verifyEmail(request.token());
            return ResponseEntity.ok(Map.of("message", "Email verificado correctamente."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Token inválido."));
        }
    }

    // Reenvío del mail de verificación.
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.ok(Map.of(
                "message", "Si el email existe y no está verificado, te reenviamos el enlace."
        ));
    }



    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken(), request.deviceId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest httpRequest,
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "false") boolean keepSession,
            @AuthenticationPrincipal UserDetails userDetails) {
        String token = extraerBearerToken(httpRequest);
        String ip = obtenerIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        authService.logout(token, deviceId, userDetails.getUsername(), ip, userAgent, keepSession);
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada correctamente"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.ok(Map.of("message", "Si el email existe, recibirás instrucciones de reset"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Contraseña restablecida correctamente"));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionInfo>> getSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.getSessions(userDetails.getUsername()));
    }


    @DeleteMapping("/sessions/{deviceId}")
    public ResponseEntity<Map<String, String>> revokeSession(
            @PathVariable String deviceId,
            @AuthenticationPrincipal UserDetails userDetails) {
        authService.revokeSession(userDetails.getUsername(), deviceId);
        return ResponseEntity.ok(Map.of("message", "Sesión revocada"));
    }

    private String extraerBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String obtenerIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
