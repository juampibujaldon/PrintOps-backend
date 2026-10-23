package com.printops.demo.service;

import com.printops.demo.dto.IntegrationDTO;
import com.printops.demo.dto.PrinterTelemetryDTO;
import com.printops.demo.dto.TelemetryReport;
import com.printops.demo.entity.*;
import com.printops.demo.repository.PrinterIntegrationRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.PrinterTelemetryRepository;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.TenantContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

// Integración con impresoras vía un agente local (en la red del taller). El backend en la
// nube no puede llegar a OctoPrint/Moonraker/Bambu en una LAN: el agente lee cada impresora
// y empuja su estado acá, autenticado con un token por impresora.
@Service
public class PrinterIntegrationService {

    static final Duration ONLINE_WINDOW = Duration.ofMinutes(2);
    private static final String TOKEN_PREFIX = "pod_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PrinterIntegrationRepository integrationRepository;
    private final PrinterTelemetryRepository telemetryRepository;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final PrinterUsageService usageService;
    private final PrintJobService jobService;
    private final NotificationService notificationService;
    private final LiveUpdates liveUpdates;

    public PrinterIntegrationService(PrinterIntegrationRepository integrationRepository,
                                     PrinterTelemetryRepository telemetryRepository,
                                     PrinterRepository printerRepository, UserRepository userRepository,
                                     PrinterUsageService usageService, PrintJobService jobService,
                                     NotificationService notificationService, LiveUpdates liveUpdates) {
        this.integrationRepository = integrationRepository;
        this.telemetryRepository = telemetryRepository;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.usageService = usageService;
        this.jobService = jobService;
        this.notificationService = notificationService;
        this.liveUpdates = liveUpdates;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // ── Gestión (supervisor) ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Optional<IntegrationDTO> get(Long printerId) {
        Printer p = findPrinter(printerId);
        return integrationRepository.findByPrinterId(p.getId()).map(i -> toDTO(i, null));
    }

    // Crea la integración o rota el token (el anterior deja de funcionar).
    @Transactional
    public IntegrationDTO createOrRotate(Long printerId, IntegrationType type) {
        Printer p = findPrinter(printerId);
        String token = newToken();
        PrinterIntegration i = integrationRepository.findByPrinterId(p.getId()).orElseGet(PrinterIntegration::new);
        i.setPrinter(p);
        i.setWorkspaceId(p.getWorkspaceId());
        i.setType(type != null ? type : IntegrationType.OTHER);
        i.setTokenHash(hash(token));
        i.setTokenHint("…" + token.substring(token.length() - 4));
        return toDTO(integrationRepository.save(i), token);
    }

    @Transactional
    public void delete(Long printerId) {
        Printer p = findPrinter(printerId);
        integrationRepository.findByPrinterId(p.getId()).ifPresent(integrationRepository::delete);
        telemetryRepository.findById(p.getId()).ifPresent(telemetryRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<PrinterTelemetryDTO> telemetry() {
        return telemetryRepository.findByWorkspaceId(wsId()).stream().map(PrinterIntegrationService::toDTO).toList();
    }

    // ── Ingesta (agente) ────────────────────────────────────────────────────
    @Transactional
    public PrinterTelemetryDTO ingest(String rawToken, TelemetryReport r) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            throw new BadCredentialsException("Token de dispositivo inválido");
        }
        PrinterIntegration integration = integrationRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Token de dispositivo inválido"));
        Printer printer = integration.getPrinter();
        Instant now = Instant.now();
        integration.setLastSeenAt(now);

        String state = normalizeState(r.state());
        PrinterTelemetry t = telemetryRepository.findById(printer.getId()).orElseGet(() -> {
            PrinterTelemetry n = new PrinterTelemetry();
            n.setPrinterId(printer.getId());
            return n;
        });
        String previous = t.getState();
        t.setWorkspaceId(printer.getWorkspaceId());
        t.setState(state);
        t.setProgress(r.progress());
        t.setNozzleTemp(r.nozzleTemp());
        t.setBedTemp(r.bedTemp());
        t.setJobName(r.jobName());
        t.setRemainingSeconds(r.remainingSeconds());
        t.setMessage(r.message());
        t.setUpdatedAt(now);
        telemetryRepository.save(t);

        // Uso real informado por la impresora al terminar un trabajo.
        Double hours = null;
        Double grams = null;
        if (r.usage() != null) {
            hours = r.usage().printSeconds() != null ? r.usage().printSeconds() / 3600.0 : 0.0;
            grams = r.usage().filamentGrams() != null ? r.usage().filamentGrams() : 0.0;
            usageService.recordUsage(printer, hours, grams, null, null, "telemetría de la impresora");
        }

        // Transiciones de estado → cola de trabajos y avisos.
        if ("PRINTING".equals(state) && !"PRINTING".equals(previous) && !"PAUSED".equals(previous)) {
            jobService.onPrinterStartedPrinting(printer);
        }
        if ("FINISHED".equals(state) && ("PRINTING".equals(previous) || "PAUSED".equals(previous) || r.usage() != null)) {
            jobService.onPrinterFinished(printer, hours, grams);
        }
        if ("ERROR".equals(state) && !"ERROR".equals(previous)) {
            jobService.onPrinterError(printer, r.message());
            String msg = "La impresora " + label(printer) + " informó un error"
                    + (r.message() != null ? ": " + r.message() : ".");
            for (User m : userRepository.findByRoleAndWorkspaceId(Role.MANAGER, printer.getWorkspaceId())) {
                notificationService.createFor(m.getId(), "PRINTER_ERROR", msg, null);
                liveUpdates.toUser(m.getId(), "notification", Map.of("title", "Error de impresora", "body", msg));
            }
        }

        PrinterTelemetryDTO dto = toDTO(t);
        liveUpdates.toWorkspace(printer.getWorkspaceId(), "telemetry", telemetryPayload(dto));
        return dto;
    }

    private static Map<String, Object> telemetryPayload(PrinterTelemetryDTO d) {
        Map<String, Object> m = new HashMap<>();
        m.put("printerId", d.printerId());
        m.put("state", d.state());
        m.put("online", d.online());
        m.put("updatedAt", d.updatedAt().toString());
        if (d.progress() != null) m.put("progress", d.progress());
        if (d.nozzleTemp() != null) m.put("nozzleTemp", d.nozzleTemp());
        if (d.bedTemp() != null) m.put("bedTemp", d.bedTemp());
        if (d.jobName() != null) m.put("jobName", d.jobName());
        if (d.remainingSeconds() != null) m.put("remainingSeconds", d.remainingSeconds());
        if (d.message() != null) m.put("message", d.message());
        return m;
    }

    static String normalizeState(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        return PrinterTelemetry.STATES.contains(s) ? s : "IDLE";
    }

    private Printer findPrinter(Long printerId) {
        return printerRepository.findByIdAndWorkspaceId(printerId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada"));
    }

    private static String label(Printer p) {
        return p.getName() != null ? p.getName() : p.getModel() + " (" + p.getSerialNumber() + ")";
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean online(Instant lastSeen) {
        return lastSeen != null && lastSeen.isAfter(Instant.now().minus(ONLINE_WINDOW));
    }

    private static IntegrationDTO toDTO(PrinterIntegration i, String token) {
        return new IntegrationDTO(i.getPrinter().getId(), i.getType().name(), i.getTokenHint(), i.getCreatedAt(),
                i.getLastSeenAt(), online(i.getLastSeenAt()), token);
    }

    static PrinterTelemetryDTO toDTO(PrinterTelemetry t) {
        boolean online = online(t.getUpdatedAt());
        return new PrinterTelemetryDTO(t.getPrinterId(), online ? t.getState() : "OFFLINE", t.getProgress(),
                t.getNozzleTemp(), t.getBedTemp(), t.getJobName(), t.getRemainingSeconds(), t.getMessage(),
                t.getUpdatedAt(), online);
    }
}
