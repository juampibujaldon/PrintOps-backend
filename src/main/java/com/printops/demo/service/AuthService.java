// src/main/java/com/printops/demo/service/AuthService.java
package com.printops.demo.service;

import com.printops.demo.dto.AuthResponse;
import com.printops.demo.dto.GoogleLoginResult;
import com.printops.demo.dto.GoogleOnboardingResponse;
import com.printops.demo.exception.GoogleNotConfiguredException;
import com.printops.demo.dto.SessionInfo;
import com.printops.demo.entity.*;
import com.printops.demo.exception.EmailAlreadyRegisteredException;
import com.printops.demo.exception.EmailNotVerifiedException;
import com.printops.demo.exception.InvalidInviteException;
import com.printops.demo.repository.*;
import com.printops.demo.security.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository blacklistRepository;
    private final LoginAuditRepository auditRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceInviteRepository inviteRepository;
    private final GoogleTokenVerifier googleVerifier;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Value("${jwt.refresh-expiration-remember}")
    private long refreshExpirationRemember;

    @Value("${jwt.expiration}")
    private long accessExpiration;

    @Value("${app.base-url}")
    private String appBaseUrl;

    private static final SecureRandom RANDOM = new SecureRandom();

    // Intentos fallidos de login, compartidos entre réplicas (RateLimiter sobre Redis):
    // 5 por IP o 10 por cuenta en 15 minutos bloquean hasta que vence la ventana. El límite
    // por cuenta frena la fuerza bruta distribuida desde muchas IPs contra un mismo email.
    private final RateLimiter rateLimiter;
    private static final int MAX_INTENTOS = 5;
    private static final int MAX_INTENTOS_EMAIL = 10;
    private static final Duration VENTANA_BLOQUEO = Duration.ofMinutes(15);
    private static final long VERIFICATION_TOKEN_TTL_SECONDS = 24 * 3600L;
    private static final long INVITE_TOKEN_TTL_SECONDS = 7 * 24 * 3600L;
    private static final long RESET_TOKEN_TTL_SECONDS = 3600L;
    private static final long ONBOARDING_TOKEN_TTL_MS = 10 * 60 * 1000L;

    public AuthService(AuthenticationManager authManager, JwtService jwtService,
                       UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       TokenBlacklistRepository blacklistRepository, LoginAuditRepository auditRepository,
                       PasswordEncoder passwordEncoder, EmailService emailService,
                       WorkspaceRepository workspaceRepository, WorkspaceInviteRepository inviteRepository,
                       GoogleTokenVerifier googleVerifier, RateLimiter rateLimiter) {
        this.googleVerifier = googleVerifier;
        this.rateLimiter = rateLimiter;
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.blacklistRepository = blacklistRepository;
        this.auditRepository = auditRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.workspaceRepository = workspaceRepository;
        this.inviteRepository = inviteRepository;
    }

    @Transactional
    public AuthResponse login(String email, String password, String deviceId,
                              boolean rememberMe, String ip, String userAgent) {
        // Verificar si la IP o la cuenta están bloqueadas
        rejectIfLocked(ip);
        if (!rateLimiter.peek(emailKey(email), MAX_INTENTOS_EMAIL).allowed()) {
            throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos. Espere 15 minutos.");
        }

        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException e) {
            registrarAudit(null, email, ip, userAgent, LoginAudit.Action.FAILED);
            incrementarIntentos(ip);
            rateLimiter.hit(emailKey(email), MAX_INTENTOS_EMAIL, VENTANA_BLOQUEO);
            throw new BadCredentialsException("Credenciales inválidas");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        // Removida la verificación por mail.

        // Login exitoso: limpiar intentos fallidos
        rateLimiter.reset(ipKey(ip));
        rateLimiter.reset(emailKey(email));

        String accessToken = jwtService.generateAccessToken(email, user.getRole().name(), user.getWorkspaceId());

        // Revocar sesión anterior del mismo dispositivo si existe
        refreshTokenRepository.revokeByUserAndDeviceId(user, deviceId);

        RefreshToken refreshToken = crearRefreshToken(user, deviceId, rememberMe);
        registrarAudit(user.getId(), email, ip, userAgent, LoginAudit.Action.LOGIN);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    @Transactional
    public void register(String email, String password, String inviteToken, String workspaceName) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyRegisteredException("El email ya está registrado. Iniciá sesión o recuperá tu contraseña.");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setEmailVerified(true); // Auto-verificar sin email

        assignRoleAndWorkspace(user, email, inviteToken, workspaceName);
        userRepository.save(user);
    }

    // Con inviteToken → TECNICO que se une a un workspace existente.
    // Sin inviteToken → MANAGER que crea su propio workspace. Compartido por register y Google.
    private void assignRoleAndWorkspace(User user, String email, String inviteToken, String workspaceName) {
        if (inviteToken != null && !inviteToken.isBlank()) {
            WorkspaceInvite invite = inviteRepository.findByToken(inviteToken)
                    .orElseThrow(() -> new InvalidInviteException(InvalidInviteException.Reason.NOT_FOUND,
                            "El código de invitación no existe. Revisá que esté completo."));
            if (invite.isAccepted()) {
                throw new InvalidInviteException(InvalidInviteException.Reason.USED,
                        "Esta invitación ya fue usada.");
            }
            if (Instant.now().isAfter(invite.getExpiresAt())) {
                throw new InvalidInviteException(InvalidInviteException.Reason.EXPIRED,
                        "La invitación expiró. Pedí una nueva al administrador del taller.");
            }
            // La invitación es personal: solo sirve para el email invitado.
            if (invite.getEmail() != null && !invite.getEmail().equalsIgnoreCase(email)) {
                throw new InvalidInviteException(InvalidInviteException.Reason.WRONG_EMAIL,
                        "Esta invitación fue emitida para otro email.");
            }
            user.setRole(Role.TECNICO);
            user.setWorkspaceId(invite.getWorkspaceId());
            invite.setAccepted(true);
            inviteRepository.save(invite);
        } else {
            Workspace ws = new Workspace();
            ws.setName(workspaceName != null && !workspaceName.isBlank() ? workspaceName : ("Taller de " + email));
            ws = workspaceRepository.save(ws);
            user.setRole(Role.MANAGER);
            user.setWorkspaceId(ws.getId());
        }
    }

    // ── Iniciar sesión con Google ────────────────────────────────────────────

    // noRollbackFor: el audit FAILED de una verificación fallida tiene que persistir.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public GoogleLoginResult loginWithGoogle(String idToken, String deviceId, boolean rememberMe,
                                             String ip, String userAgent) {
        requireGoogleConfigured();
        rejectIfLocked(ip);

        GoogleIdentity identity = googleVerifier.verify(idToken)
                .filter(GoogleIdentity::emailVerified)
                .filter(i -> i.subject() != null && i.email() != null)
                .orElseThrow(() -> failedGoogleAttempt(null, ip, userAgent));

        Optional<User> linked = userRepository.findByGoogleSubject(identity.subject());
        if (linked.isPresent()) {
            return new GoogleLoginResult(startSession(linked.get(), deviceId, rememberMe, ip, userAgent), null);
        }

        Optional<User> sameEmail = userRepository.findByEmail(identity.email());
        if (sameEmail.isPresent()) {
            User user = sameEmail.get();
            if (user.getGoogleSubject() != null) {
                // Ese email ya está vinculado a OTRA cuenta de Google: no se re-vincula.
                throw failedGoogleAttempt(identity.email(), ip, userAgent);
            }
            requireEnabled(user);
            // El registro con contraseña no verifica el email: un tercero pudo haberlo
            // pre-registrado. Al vincular se cierran TODAS las sesiones previas.
            user.setGoogleSubject(identity.subject());
            user.setEmailVerified(true);
            userRepository.save(user);
            refreshTokenRepository.revokeAllByUser(user);
            return new GoogleLoginResult(startSession(user, deviceId, rememberMe, ip, userAgent), null);
        }

        String onboarding = jwtService.generateOnboardingToken(
                identity.subject(), identity.email(), identity.name(), ONBOARDING_TOKEN_TTL_MS);
        return new GoogleLoginResult(null, new GoogleOnboardingResponse(onboarding, identity.email(), identity.name()));
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthResponse completeGoogleSignup(String onboardingToken, String deviceId, boolean rememberMe,
                                             String inviteToken, String workspaceName,
                                             String ip, String userAgent) {
        requireGoogleConfigured();
        rejectIfLocked(ip);

        JwtService.OnboardingClaims claims = jwtService.parseOnboardingToken(onboardingToken)
                .orElseThrow(() -> failedGoogleAttempt(null, ip, userAgent));
        String email = claims.email();

        if (userRepository.findByEmail(email).isPresent() || userRepository.findByGoogleSubject(claims.googleSubject()).isPresent()) {
            throw new EmailAlreadyRegisteredException("El email ya está registrado. Iniciá sesión con Google nuevamente.");
        }

        User user = new User();
        user.setEmail(email);
        // Columna NOT NULL: se guarda el hash de un secreto aleatorio que nadie conoce.
        user.setPassword(passwordEncoder.encode(randomSecret()));
        user.setEnabled(true);
        user.setEmailVerified(true); // Google ya verificó el email
        user.setGoogleSubject(claims.googleSubject());
        assignRoleAndWorkspace(user, email, inviteToken, workspaceName);
        user = userRepository.save(user);

        return startSession(user, deviceId, rememberMe, ip, userAgent);
    }

    private void requireGoogleConfigured() {
        if (!googleVerifier.isConfigured()) throw new GoogleNotConfiguredException();
    }

    private void requireEnabled(User user) {
        if (!user.isEnabled()) throw new DisabledException("Cuenta deshabilitada");
    }

    // Registra el intento fallido (audit + contador por IP) y devuelve el 401 a lanzar.
    private BadCredentialsException failedGoogleAttempt(String email, String ip, String userAgent) {
        // login_audit.email es NOT NULL: sin email conocido se registra un marcador.
        registrarAudit(null, email != null ? email : "(google: token no verificado)", ip, userAgent, LoginAudit.Action.FAILED);
        incrementarIntentos(ip);
        return new BadCredentialsException("No se pudo verificar la cuenta de Google");
    }

    // Cierre común de un login exitoso (igual que el de contraseña).
    private AuthResponse startSession(User user, String deviceId, boolean rememberMe, String ip, String userAgent) {
        requireEnabled(user);
        rateLimiter.reset(ipKey(ip));
        String accessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name(), user.getWorkspaceId());
        refreshTokenRepository.revokeByUserAndDeviceId(user, deviceId);
        RefreshToken refreshToken = crearRefreshToken(user, deviceId, rememberMe);
        registrarAudit(user.getId(), user.getEmail(), ip, userAgent, LoginAudit.Action.LOGIN);
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    private void rejectIfLocked(String ip) {
        if (isIpBloqueada(ip)) {
            throw new LockedException("IP bloqueada por múltiples intentos fallidos. Espere 15 minutos.");
        }
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
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
                String token = String.format("%06d", RANDOM.nextInt(1_000_000));
                user.setVerificationToken(token);
                user.setVerificationTokenExpiry(Instant.now().plusSeconds(VERIFICATION_TOKEN_TTL_SECONDS));
                userRepository.save(user);
                emailService.sendVerificationEmail(email, token);
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
        String newAccessToken = jwtService.generateAccessToken(user.getEmail(), user.getRole().name(), user.getWorkspaceId());

        return buildAuthResponse(newAccessToken, refreshToken, user);
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

    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            Instant expiry = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS);
            user.setResetToken(resetToken);
            user.setResetTokenExpiry(expiry);
            userRepository.save(user);
            try {
                emailService.sendPasswordResetEmail(email, resetToken);
            } catch (RuntimeException e) {
                // Un 500 acá revelaría que el email existe; se loguea y se responde igual.
                log.error("No se pudo enviar el email de reset a {}", email, e);
            }
        });
        // Siempre responde OK para no revelar si el email existe
    }

    // Restablece la contraseña con el token enviado por email.
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new BadCredentialsException("Token de reset inválido."));

        if (user.getResetTokenExpiry() != null && Instant.now().isAfter(user.getResetTokenExpiry())) {
            throw new BadCredentialsException("El enlace de reset expiró. Solicitá uno nuevo.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        // Por seguridad: se revocan todas las sesiones activas del usuario.
        refreshTokenRepository.revokeAllByUser(user);
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> getSessions(String email) {        User user = userRepository.findByEmail(email)
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

    // Limpieza automática cada hora
    @Scheduled(fixedRate = 3_600_000)
    @SchedulerLock(name = "auth-token-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
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
        rt.setRememberMe(rememberMe);
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
        return !rateLimiter.peek(ipKey(ip), MAX_INTENTOS).allowed();
    }

    private void incrementarIntentos(String ip) {
        rateLimiter.hit(ipKey(ip), MAX_INTENTOS, VENTANA_BLOQUEO);
    }

    private static String ipKey(String ip) {
        return "login-fail:ip:" + ip;
    }

    private static String emailKey(String email) {
        return "login-fail:email:" + (email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private AuthResponse buildAuthResponse(String accessToken, RefreshToken refreshToken, User user) {
        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                (int) (accessExpiration / 1000),
                new AuthResponse.UserInfo(user.getId(), user.getEmail(), user.getRole().name(), user.getWorkspaceId()),
                refreshToken.getExpiresAt(),
                refreshToken.isRememberMe()
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
