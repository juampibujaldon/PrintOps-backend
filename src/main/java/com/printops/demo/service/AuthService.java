// src/main/java/com/printops/demo/service/AuthService.java
package com.printops.demo.service;

import com.printops.demo.dto.AuthResponse;
import com.printops.demo.dto.SessionInfo;
import com.printops.demo.entity.*;
import com.printops.demo.exception.EmailNotVerifiedException;
import com.printops.demo.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository blacklistRepository;
    private final LoginAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${jwt.refresh-expiration-remember}")
    private long refreshExpirationRemember;

    @Value("${app.base-url}")
    private String appBaseUrl;

    // Rate limiting: IP -> intentos fallidos
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lockedUntil = new ConcurrentHashMap<>();
    private static final int MAX_INTENTOS = 5;
    private static final long BLOQUEO_MS = 15 * 60 * 1000L;
    private static final long VERIFICATION_TOKEN_TTL_SECONDS = 24 * 3600L;

    public AuthService(AuthenticationManager authManager, JwtService jwtService,
                       UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       TokenBlacklistRepository blacklistRepository, LoginAuditRepository auditRepository,
                       PasswordEncoder passwordEncoder, EmailService emailService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistRepository = blacklistRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse login(String email, String password, String deviceId,
                              boolean rememberMe, String ip, String userAgent) {
        // Verificar si la IP está bloqueada
        if (isIpBloqueada(ip)) {
            throw new LockedException("IP bloqueada por múltiples intentos fallidos. Espere 15 minutos.");
        }

        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException e) {
            registrarAudit(null, email, ip, userAgent, LoginAudit.Action.FAILED);
            incrementarIntentos(ip);
            throw new BadCredentialsException("Credenciales inválidas");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        // Verificación por mail: no se permite iniciar sesión hasta verificar.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Debes verificar tu email antes de iniciar sesión.");
        }

        // Login exitoso: limpiar intentos fallidos
        failedAttempts.remove(ip);
        lockedUntil.remove(ip);

        String accessToken = jwtService.generateAccessToken(email, user.getRole().name());

        // Revocar sesión anterior del mismo dispositivo si existe
        refreshTokenRepository.revokeByUserAndDeviceId(user, deviceId);

        RefreshToken refreshToken = crearRefreshToken(user, deviceId, rememberMe);
        registrarAudit(user.getId(), email, ip, userAgent, LoginAudit.Action.LOGIN);

        return buildAuthResponse(accessToken, refreshToken.getToken(), user);
    }

    @Transactional
    public void register(String email, String password) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BadCredentialsException("El email ya está registrado");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.TECNICO);
        user.setEnabled(true);
        user.setEmailVerified(false);

        // Token de verificación único con expiración de 24 hs.
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_SECONDS));

        userRepository.save(user);

        // Envío del magic link. Si falla, la transacción se revierte y el registro no persiste.
        emailService.sendVerificationEmail(email, buildVerificationUrl(token));
    }

    // Verifica el email usando el token del magic link.
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new BadCredentialsException("Token de verificación inválido."));

        if (user.isEmailVerified()) {
            return; // Idempotente: ya estaba verificado.
        }

        if (user.getVerificationTokenExpiry() != null
                && Instant.now().isAfter(user.getVerificationTokenExpiry())) {
            throw new BadCredentialsException("El enlace de verificación expiró. Solicitá uno nuevo.");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
    }

    // Reenvía el mail de verificación (solo si el usuario existe y aún no verificó).
    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                String token = UUID.randomUUID().toString();
                user.setVerificationToken(token);
                user.setVerificationTokenExpiry(Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_SECONDS));
                userRepository.save(user);
                emailService.sendVerificationEmail(email, buildVerificationUrl(token));
            }
        });
        // Siempre responde OK para no revelar si el email existe.
    }

    private String buildVerificationUrl(String token) {
        return appBaseUrl + "/api/auth/verify-email?token=" + token;
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String deviceId) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(rawRefreshToken)
                .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

        if (refreshToken.isRevoked() || refreshToken.isExpired()) {
            throw new BadCredentialsException("Refresh token expirado o revocado");
        }

        if (!refreshToken.getDeviceId().equals(deviceId)) {
            throw new BadCredentialsException("DeviceId no coincide");
        }

        User user = refreshToken.getUser();
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name());

        return buildAuthResponse(newAccessToken, rawRefreshToken, user);
    }

    @Transactional
    public void logout(String accessToken, String deviceId, String email, String ip, String userAgent,
                       boolean keepSession) {
        // Agregar access token a la blacklist siempre
        agregarABlacklist(accessToken);

        userRepository.findByEmail(email).ifPresent(user -> {
            // keepSession=true: el usuario tiene biometría habilitada.
            // Solo se invalida el access token (ya blacklisteado arriba).
            // El refresh token permanece válido para que Face ID pueda renovar la sesión.
            // keepSession=false: logout completo, se revoca también el refresh token.
            if (!keepSession) {
                refreshTokenRepository.revokeByUserAndDeviceId(user, deviceId);
            }
            registrarAudit(user.getId(), email, ip, userAgent, LoginAudit.Action.LOGOUT);
        });
    }

    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plusSeconds(3600);
            // Simular envío por email (log en consola)
            System.out.printf("[EMAIL SIMULADO] Reset password para %s - Token: %s - Expira: %s%n",
                    email, resetToken, expiry);
        });
        // Siempre responde OK para no revelar si el email existe
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> getSessions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));

        return refreshTokenRepository
                .findByUserAndRevokedFalseAndExpiresAtAfter(user, Instant.now())
                .stream()
                .map(rt -> new SessionInfo(rt.getDeviceId(), rt.getCreatedAt(), rt.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void revokeSession(String email, String deviceId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
        refreshTokenRepository.revokeByUserAndDeviceId(user, deviceId);
    }

    public boolean isTokenRevocado(String token) {
        String hash = hashToken(token);
        return blacklistRepository.existsByTokenHash(hash);
    }

    // Limpieza automática cada hora
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void limpiarTokensExpirados() {
        Instant ahora = Instant.now();
        blacklistRepository.deleteExpired(ahora);
        refreshTokenRepository.deleteExpired(ahora);
    }

    private RefreshToken crearRefreshToken(User user, String deviceId, boolean rememberMe) {
        long expMs = rememberMe ? refreshExpirationRemember : refreshExpiration;
        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUser(user);
        rt.setDeviceId(deviceId);
        rt.setExpiresAt(Instant.now().plusMillis(expMs));
        rt.setRevoked(false);
        return refreshTokenRepository.save(rt);
    }

    private void agregarABlacklist(String token) {
        try {
            Date expiry = jwtService.extractExpiration(token);
            TokenBlacklist entry = new TokenBlacklist();
            entry.setTokenHash(hashToken(token));
            entry.setExpiresAt(expiry.toInstant());
            blacklistRepository.save(entry);
        } catch (Exception ignored) {
            // Si el token ya no es parseable, no hace falta blacklistear
        }
    }

    private void registrarAudit(Long userId, String email, String ip,
                                 String userAgent, LoginAudit.Action action) {
        LoginAudit audit = new LoginAudit();
        audit.setUserId(userId);
        audit.setEmail(email);
        audit.setIpAddress(ip);
        audit.setUserAgent(userAgent);
        audit.setAction(action);
        auditRepository.save(audit);
    }

    private boolean isIpBloqueada(String ip) {
        Instant bloqueo = lockedUntil.get(ip);
        if (bloqueo == null) return false;
        if (Instant.now().isAfter(bloqueo)) {
            lockedUntil.remove(ip);
            failedAttempts.remove(ip);
            return false;
        }
        return true;
    }

    private void incrementarIntentos(String ip) {
        int intentos = failedAttempts.merge(ip, 1, Integer::sum);
        if (intentos >= MAX_INTENTOS) {
            lockedUntil.put(ip, Instant.now().plusMillis(BLOQUEO_MS));
        }
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        return new AuthResponse(
                accessToken,
                refreshToken,
                900,
                new AuthResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name())
        );
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}
