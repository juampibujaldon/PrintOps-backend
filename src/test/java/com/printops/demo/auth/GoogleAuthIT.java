package com.printops.demo.auth;

import com.printops.demo.entity.*;
import com.printops.demo.repository.*;
import com.printops.demo.service.GoogleIdentity;
import com.printops.demo.service.GoogleTokenVerifier;
import com.printops.demo.service.JwtService;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

// "Iniciar sesion con Google": HTTP real (Tomcat) + Postgres de Testcontainers +
// verificador de Google falso (no se pueden usar tokens reales de Google en tests).
@Import(GoogleAuthIT.FakeGoogleConfig.class)
class GoogleAuthIT extends IntegrationTestBase {

    static class FakeVerifier implements GoogleTokenVerifier {
        final Map<String, GoogleIdentity> tokens = new ConcurrentHashMap<>();
        volatile boolean configured = true;

        @Override public boolean isConfigured() { return configured; }
        @Override public Optional<GoogleIdentity> verify(String idToken) { return Optional.ofNullable(tokens.get(idToken)); }
    }

    @TestConfiguration
    static class FakeGoogleConfig {
        @Bean @Primary FakeVerifier fakeGoogleVerifier() { return new FakeVerifier(); }
    }

    @LocalServerPort int port;
    @Autowired FakeVerifier google;
    @Autowired JwtService jwtService;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceInviteRepository invites;
    @Autowired LoginAuditRepository audits;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        google.tokens.clear();
        google.configured = true;
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private String uniqueEmail() { return "g-" + UUID.randomUUID() + "@gmail.test"; }

    // IP distinta por llamada: el bloqueo por IP es en memoria y compartido por todo el contexto.
    private String ip() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(250) + "." + r.nextInt(250) + "." + r.nextInt(250);
    }

    private String googleToken(String subject, String email, boolean verified, String name) {
        String t = "tok-" + UUID.randomUUID();
        google.tokens.put(t, new GoogleIdentity(subject, email, verified, name));
        return t;
    }

    private HttpResponse<String> post(String path, String json, String ip) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip)
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> google(String idToken, String deviceId, String ip) throws Exception {
        return post("/api/auth/google",
                "{\"idToken\":\"" + idToken + "\",\"deviceId\":\"" + deviceId + "\",\"rememberMe\":false}", ip);
    }

    private HttpResponse<String> complete(String onboarding, String extra, String ip) throws Exception {
        return post("/api/auth/google/complete",
                "{\"onboardingToken\":\"" + onboarding + "\",\"deviceId\":\"dev-1\",\"rememberMe\":false" + extra + "}", ip);
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(m.find()).as("campo %s en %s", name, json).isTrue();
        return m.group(1);
    }

    private String onboardingFor(String email, String sub, String name) throws Exception {
        HttpResponse<String> r = google(googleToken(sub, email, true, name), "dev-1", ip());
        assertThat(r.statusCode()).isEqualTo(202);
        return field(r.body(), "onboardingToken");
    }

    private RefreshToken saveRefresh(User u, String deviceId) {
        RefreshToken rt = new RefreshToken();
        rt.setToken(UUID.randomUUID().toString());
        rt.setUser(u);
        rt.setDeviceId(deviceId);
        rt.setExpiresAt(Instant.now().plusSeconds(3600));
        rt.setRevoked(false);
        return refreshTokens.save(rt);
    }

    // ── POST /api/auth/google ────────────────────────────────────────────────
    @Test
    void newEmailGets202WithOnboardingTokenAndNoAccountIsCreated() throws Exception {
        String email = uniqueEmail();
        HttpResponse<String> r = google(googleToken("sub-" + email, email, true, "Ana Perez"), "dev-1", ip());

        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(field(r.body(), "email")).isEqualTo(email);
        assertThat(field(r.body(), "name")).isEqualTo("Ana Perez");
        assertThat(field(r.body(), "onboardingToken")).isNotBlank();
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void newEmailWithoutNameReturnsNullName() throws Exception {
        String email = uniqueEmail();
        HttpResponse<String> r = google(googleToken("sub-" + email, email, true, null), "dev-1", ip());
        assertThat(r.statusCode()).isEqualTo(202);
        assertThat(r.body()).contains("\"name\":null");
    }

    @Test
    void existingPasswordAccountIsLinkedAndAllItsRefreshTokensRevoked() throws Exception {
        User u = newUser("pw", Role.MANAGER, 777_001L);
        RefreshToken old = saveRefresh(u, "other-device");
        String oldPassword = u.getPassword();

        HttpResponse<String> r = google(googleToken("sub-" + u.getEmail(), u.getEmail(), true, "X"), "dev-1", ip());

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(field(r.body(), "accessToken")).isNotBlank();
        assertThat(field(r.body(), "refreshToken")).isNotBlank();
        assertThat(r.body()).contains("\"expiresIn\"").contains("\"workspaceId\":777001").contains("\"role\":\"MANAGER\"");
        User reloaded = userRepository.findByEmail(u.getEmail()).orElseThrow();
        assertThat(reloaded.getGoogleSubject()).isEqualTo("sub-" + u.getEmail());
        assertThat(reloaded.getPassword()).isEqualTo(oldPassword);

        HttpResponse<String> refresh = post("/api/auth/refresh",
                "{\"refreshToken\":\"" + old.getToken() + "\",\"deviceId\":\"other-device\"}", ip());
        assertThat(refresh.statusCode()).isEqualTo(401);
        // La sesion nueva si funciona.
        HttpResponse<String> fresh = post("/api/auth/refresh",
                "{\"refreshToken\":\"" + field(r.body(), "refreshToken") + "\",\"deviceId\":\"dev-1\"}", ip());
        assertThat(fresh.statusCode()).isEqualTo(200);
    }

    @Test
    void alreadyLinkedUserLogsInWithoutRevokingOtherSessions() throws Exception {
        User u = newUser("lnk", Role.TECNICO, 777_002L);
        u.setGoogleSubject("sub-linked-" + u.getId());
        userRepository.save(u);
        RefreshToken other = saveRefresh(u, "other");

        // Mismo subject: el email de Google puede haber cambiado, el subject manda.
        HttpResponse<String> r = google(googleToken(u.getGoogleSubject(), "changed@gmail.test", true, "N"), "dev-1", ip());

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("\"id\":" + u.getId()).contains("\"role\":\"TECNICO\"");
        assertThat(refreshTokens.findById(other.getId()).orElseThrow().isRevoked()).isFalse();
        assertThat(audits.findAll()).anyMatch(a -> u.getId().equals(a.getUserId()) && a.getAction() == LoginAudit.Action.LOGIN);
    }

    @Test
    void invalidGoogleTokenIs401() throws Exception {
        HttpResponse<String> r = google("garbage", "dev-1", ip());
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(r.body()).contains("\"error\"");
    }

    @Test
    void unverifiedGoogleEmailIs401EvenForExistingAccount() throws Exception {
        User u = newUser("unv", Role.MANAGER, 777_003L);
        HttpResponse<String> r = google(googleToken("sub-unv", u.getEmail(), false, "X"), "dev-1", ip());
        assertThat(r.statusCode()).isEqualTo(401);
        assertThat(userRepository.findByEmail(u.getEmail()).orElseThrow().getGoogleSubject()).isNull();
    }

    @Test
    void failedGoogleVerificationsCountTowardIpLockout() throws Exception {
        String ip = ip();
        for (int i = 0; i < 5; i++) assertThat(google("bad-" + i, "dev-1", ip).statusCode()).isEqualTo(401);
        // Aun con un token valido, la IP ya esta bloqueada.
        String email = uniqueEmail();
        assertThat(google(googleToken("s-" + email, email, true, "A"), "dev-1", ip).statusCode()).isEqualTo(429);
    }

    @Test
    void disabledUserIs403() throws Exception {
        User u = newUser("dis", Role.MANAGER, 777_004L);
        u.setEnabled(false);
        userRepository.save(u);
        HttpResponse<String> r = google(googleToken("sub-dis", u.getEmail(), true, "X"), "dev-1", ip());
        assertThat(r.statusCode()).isEqualTo(403);
    }

    @Test
    void notConfiguredIs503() throws Exception {
        google.configured = false;
        assertThat(google("anything", "dev-1", ip()).statusCode()).isEqualTo(503);
        assertThat(complete("anything", "", ip()).statusCode()).isEqualTo(503);
    }

    @Test
    void missingFieldsIs400() throws Exception {
        assertThat(post("/api/auth/google", "{\"deviceId\":\"d\"}", ip()).statusCode()).isEqualTo(400);
        assertThat(post("/api/auth/google", "{\"idToken\":\"t\"}", ip()).statusCode()).isEqualTo(400);
    }

    // ── POST /api/auth/google/complete ───────────────────────────────────────
    @Test
    void completeWithWorkspaceNameCreatesManagerAndWorkspace() throws Exception {
        String email = uniqueEmail();
        String onboarding = onboardingFor(email, "sub-" + email, "Ana");

        HttpResponse<String> r = complete(onboarding, ",\"workspaceName\":\"Taller Ana\"", ip());

        assertThat(r.statusCode()).isEqualTo(200);
        User u = userRepository.findByEmail(email).orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.MANAGER);
        assertThat(u.getGoogleSubject()).isEqualTo("sub-" + email);
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.isEnabled()).isTrue();
        assertThat(u.getPassword()).startsWith("$2"); // hash BCrypt de un secreto aleatorio
        assertThat(workspaces.findById(u.getWorkspaceId()).orElseThrow().getName()).isEqualTo("Taller Ana");
        assertThat(field(r.body(), "accessToken")).isNotBlank();
        assertThat(r.body()).contains("\"email\":\"" + email + "\"");
        assertThat(audits.findAll()).anyMatch(a -> u.getId().equals(a.getUserId()) && a.getAction() == LoginAudit.Action.LOGIN);
    }

    @Test
    void completeWithoutWorkspaceNameUsesDefaultName() throws Exception {
        String email = uniqueEmail();
        HttpResponse<String> r = complete(onboardingFor(email, "sub-" + email, null), "", ip());
        assertThat(r.statusCode()).isEqualTo(200);
        User u = userRepository.findByEmail(email).orElseThrow();
        assertThat(workspaces.findById(u.getWorkspaceId()).orElseThrow().getName()).isEqualTo("Taller de " + email);
    }

    private WorkspaceInvite newInvite(String email, boolean accepted, Instant expiresAt) {
        WorkspaceInvite i = new WorkspaceInvite();
        i.setToken(UUID.randomUUID().toString());
        i.setEmail(email);
        i.setWorkspaceId(888_001L);
        i.setAccepted(accepted);
        i.setExpiresAt(expiresAt);
        return invites.save(i);
    }

    @Test
    void completeWithValidInviteCreatesTecnicoInInvitesWorkspace() throws Exception {
        String email = uniqueEmail();
        WorkspaceInvite inv = newInvite(email, false, Instant.now().plusSeconds(3600));

        HttpResponse<String> r = complete(onboardingFor(email, "sub-" + email, "T"),
                ",\"inviteToken\":\"" + inv.getToken() + "\"", ip());

        assertThat(r.statusCode()).isEqualTo(200);
        User u = userRepository.findByEmail(email).orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.TECNICO);
        assertThat(u.getWorkspaceId()).isEqualTo(888_001L);
        assertThat(invites.findById(inv.getId()).orElseThrow().isAccepted()).isTrue();
    }

    @Test
    void completeWithInviteForOtherEmailIsRejectedAndNothingCreated() throws Exception {
        String email = uniqueEmail();
        WorkspaceInvite inv = newInvite("someone-else@x.test", false, Instant.now().plusSeconds(3600));

        HttpResponse<String> r = complete(onboardingFor(email, "sub-" + email, "T"),
                ",\"inviteToken\":\"" + inv.getToken() + "\"", ip());

        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("INVITE_WRONG_EMAIL");
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void completeWithUnknownUsedOrExpiredInviteReportsReason() throws Exception {
        String email = uniqueEmail();
        String onboarding = onboardingFor(email, "sub-" + email, "T");
        assertThat(complete(onboarding, ",\"inviteToken\":\"nope\"", ip()).body()).contains("INVITE_NOT_FOUND");
        WorkspaceInvite used = newInvite(email, true, Instant.now().plusSeconds(3600));
        assertThat(complete(onboarding, ",\"inviteToken\":\"" + used.getToken() + "\"", ip()).body()).contains("INVITE_USED");
        WorkspaceInvite old = newInvite(email, false, Instant.now().minusSeconds(5));
        assertThat(complete(onboarding, ",\"inviteToken\":\"" + old.getToken() + "\"", ip()).body()).contains("INVITE_EXPIRED");
    }

    @Test
    void completeIs409IfAccountWasCreatedMeanwhile() throws Exception {
        String email = uniqueEmail();
        String onboarding = onboardingFor(email, "sub-" + email, "T");
        User u = newUser("race", Role.MANAGER, 777_005L);
        u.setEmail(email);
        userRepository.save(u);

        HttpResponse<String> r = complete(onboarding, ",\"workspaceName\":\"W\"", ip());

        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(r.body()).contains("EMAIL_TAKEN");
    }

    @Test
    void completeWithGarbageOrExpiredOnboardingTokenIs401() throws Exception {
        assertThat(complete("not-a-jwt", "", ip()).statusCode()).isEqualTo(401);
        String expired = jwtService.generateOnboardingToken("sub-x", "x@gmail.test", "X", -1_000L);
        assertThat(complete(expired, "", ip()).statusCode()).isEqualTo(401);
    }

    @Test
    void accessTokenIsNotAcceptedAsOnboardingToken() throws Exception {
        User u = newUser("acc", Role.MANAGER, 777_006L);
        String access = jwtService.generateAccessToken(u.getEmail(), "MANAGER", 777_006L);
        assertThat(complete(access, "", ip()).statusCode()).isEqualTo(401);
    }

    @Test
    void onboardingTokenIsNotAcceptedAsAccessTokenOnProtectedEndpoints() throws Exception {
        // Peor caso: el subject coincide con el email de un usuario real.
        User u = newUser("tgt", Role.MANAGER, 777_007L);
        String onboarding = jwtService.generateOnboardingToken(u.getEmail(), u.getEmail(), "N", 600_000L);
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/printers"))
                .header("Authorization", "Bearer " + onboarding).GET().build();
        assertThat(http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);

        // Control: el access token del mismo usuario si entra.
        HttpRequest ok = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/printers"))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(u.getEmail(), "MANAGER", 777_007L))
                .GET().build();
        assertThat(http.send(ok, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }
}
