package com.printops.demo.service;

import com.printops.demo.dto.FinishPrintJobRequest;
import com.printops.demo.dto.PrintJobDTO;
import com.printops.demo.dto.PrintJobRequest;
import com.printops.demo.entity.*;
import com.printops.demo.repository.*;
import com.printops.demo.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

// Cola de trabajos de impresión. Un presupuesto aceptado entra solo a la cola; al
// terminar un trabajo se registra el uso real en la impresora y se descuenta la bobina.
@Service
public class PrintJobService {

    // Con un agente reportando, el uso real llega por telemetría: el trabajo no lo vuelve
    // a sumar (evita contar dos veces las mismas horas).
    static final Duration AGENT_ACTIVE_WINDOW = Duration.ofMinutes(15);

    private static final Set<PrintJobStatus> ACTIVE = EnumSet.of(PrintJobStatus.QUEUED, PrintJobStatus.PRINTING);

    private final PrintJobRepository jobRepository;
    private final PrinterRepository printerRepository;
    private final FilamentSpoolRepository spoolRepository;
    private final PrinterIntegrationRepository integrationRepository;
    private final UserRepository userRepository;
    private final FilamentSpoolService spoolService;
    private final PrinterUsageService usageService;
    private final LiveUpdates liveUpdates;

    public PrintJobService(PrintJobRepository jobRepository, PrinterRepository printerRepository,
                           FilamentSpoolRepository spoolRepository, PrinterIntegrationRepository integrationRepository,
                           UserRepository userRepository, FilamentSpoolService spoolService,
                           PrinterUsageService usageService, LiveUpdates liveUpdates) {
        this.jobRepository = jobRepository;
        this.printerRepository = printerRepository;
        this.spoolRepository = spoolRepository;
        this.integrationRepository = integrationRepository;
        this.userRepository = userRepository;
        this.spoolService = spoolService;
        this.usageService = usageService;
        this.liveUpdates = liveUpdates;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // active=true: imprimiendo y en espera; false: historial (terminados, fallidos, cancelados).
    @Transactional(readOnly = true)
    public List<PrintJobDTO> list(boolean active, Long printerId) {
        Set<PrintJobStatus> statuses = active ? ACTIVE : EnumSet.complementOf(EnumSet.copyOf(ACTIVE));
        return jobRepository.findQueue(wsId(), statuses, printerId).stream().map(PrintJobService::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PrintJobDTO get(Long id) {
        return toDTO(find(id));
    }

    @Transactional
    public PrintJobDTO create(PrintJobRequest r, String email) {
        PrintJob j = new PrintJob();
        j.setWorkspaceId(wsId());
        j.setStatus(PrintJobStatus.QUEUED);
        j.setCreatedBy(userRepository.findByEmail(email).orElse(null));
        apply(j, r);
        return changed(jobRepository.save(j));
    }

    @Transactional
    public PrintJobDTO update(Long id, PrintJobRequest r) {
        PrintJob j = find(id);
        if (j.getStatus().isFinal()) {
            throw new IllegalArgumentException("Un trabajo terminado no se puede editar.");
        }
        if (j.getStatus() == PrintJobStatus.PRINTING && r.printerId() != null && j.getPrinter() != null
                && !r.printerId().equals(j.getPrinter().getId())) {
            throw new IllegalArgumentException("No se puede cambiar la impresora de un trabajo que ya se está imprimiendo.");
        }
        apply(j, r);
        return changed(jobRepository.save(j));
    }

    // Presupuesto aceptado → trabajo en cola (uno por presupuesto).
    @Transactional
    public void createFromQuote(Quote q, User actor) {
        if (q.getId() == null || jobRepository.existsByQuoteId(q.getId())) return;
        int units = q.getUnits() != null ? q.getUnits() : 1;
        PrintJob j = new PrintJob();
        j.setWorkspaceId(q.getWorkspaceId());
        j.setQuote(q);
        j.setStatus(PrintJobStatus.QUEUED);
        String what = q.getJobDescription() != null ? q.getJobDescription()
                : q.getClientName() != null ? q.getClientName() : "Trabajo";
        j.setTitle(truncate(q.getQuoteNumber() != null ? q.getQuoteNumber() + " · " + what : what, 255));
        j.setUnits(units);
        j.setEstimatedHours(nz(q.getPrintingHours()) * units);
        j.setFilamentGrams(nz(q.getFilamentGrams()) * units);
        j.setFilamentType(q.getFilamentType());
        j.setPrinter(q.getPrinter());
        j.setSpool(q.getSpool());
        j.setCreatedBy(actor);
        changed(jobRepository.save(j));
    }

    @Transactional
    public PrintJobDTO start(Long id) {
        PrintJob j = find(id);
        if (j.getStatus() != PrintJobStatus.QUEUED) {
            throw new IllegalArgumentException("Solo se puede iniciar un trabajo en espera.");
        }
        Printer p = j.getPrinter();
        if (p == null) {
            throw new IllegalArgumentException("Asigná una impresora antes de iniciar el trabajo.");
        }
        if (p.getStatus() != PrinterStatus.OPERATIVE) {
            throw new IllegalArgumentException("La impresora no está operativa.");
        }
        if (!jobRepository.findByPrinterIdAndStatusOrderByStartedAtAsc(p.getId(), PrintJobStatus.PRINTING).isEmpty()) {
            throw new IllegalArgumentException("La impresora ya tiene un trabajo en curso.");
        }
        markStarted(j);
        return changed(jobRepository.save(j));
    }

    @Transactional
    public PrintJobDTO finish(Long id, FinishPrintJobRequest r, String email) {
        PrintJob j = find(id);
        if (j.getStatus() != PrintJobStatus.PRINTING && j.getStatus() != PrintJobStatus.QUEUED) {
            throw new IllegalArgumentException("El trabajo ya está cerrado.");
        }
        User actor = userRepository.findByEmail(email).orElse(null);
        complete(j, r != null ? r.actualHours() : null, r != null ? r.actualGrams() : null,
                r != null ? r.notes() : null, actor, false);
        return changed(jobRepository.save(j));
    }

    @Transactional
    public PrintJobDTO fail(Long id, String notes) {
        PrintJob j = find(id);
        if (j.getStatus().isFinal()) throw new IllegalArgumentException("El trabajo ya está cerrado.");
        j.setStatus(PrintJobStatus.FAILED);
        j.setFinishedAt(Instant.now());
        appendNote(j, notes);
        return changed(jobRepository.save(j));
    }

    @Transactional
    public PrintJobDTO cancel(Long id) {
        PrintJob j = find(id);
        if (j.getStatus().isFinal()) throw new IllegalArgumentException("El trabajo ya está cerrado.");
        j.setStatus(PrintJobStatus.CANCELLED);
        j.setFinishedAt(Instant.now());
        return changed(jobRepository.save(j));
    }

    // Reintento de un trabajo fallido: vuelve a la cola.
    @Transactional
    public PrintJobDTO requeue(Long id) {
        PrintJob j = find(id);
        if (j.getStatus() != PrintJobStatus.FAILED && j.getStatus() != PrintJobStatus.CANCELLED) {
            throw new IllegalArgumentException("Solo se puede reintentar un trabajo fallido o cancelado.");
        }
        j.setStatus(PrintJobStatus.QUEUED);
        j.setStartedAt(null);
        j.setFinishedAt(null);
        return changed(jobRepository.save(j));
    }

    // ── Telemetría ──────────────────────────────────────────────────────────
    // La impresora empezó a imprimir: arranca el primer trabajo en espera asignado a ella.
    @Transactional
    public void onPrinterStartedPrinting(Printer printer) {
        if (!jobRepository.findByPrinterIdAndStatusOrderByStartedAtAsc(printer.getId(), PrintJobStatus.PRINTING).isEmpty()) {
            return;
        }
        jobRepository.findByPrinterIdAndStatusOrderByPriorityDescCreatedAtAsc(printer.getId(), PrintJobStatus.QUEUED)
                .stream().findFirst().ifPresent(j -> {
                    markStarted(j);
                    changed(jobRepository.save(j));
                });
    }

    // La impresora terminó: cierra el trabajo en curso con los valores medidos. El uso ya
    // lo registró la telemetría.
    @Transactional
    public void onPrinterFinished(Printer printer, Double hours, Double grams) {
        jobRepository.findByPrinterIdAndStatusOrderByStartedAtAsc(printer.getId(), PrintJobStatus.PRINTING)
                .stream().findFirst().ifPresent(j -> {
                    complete(j, hours, grams, null, null, true);
                    changed(jobRepository.save(j));
                });
    }

    @Transactional
    public void onPrinterError(Printer printer, String message) {
        jobRepository.findByPrinterIdAndStatusOrderByStartedAtAsc(printer.getId(), PrintJobStatus.PRINTING)
                .stream().findFirst().ifPresent(j -> {
                    j.setStatus(PrintJobStatus.FAILED);
                    j.setFinishedAt(Instant.now());
                    appendNote(j, message != null ? "Error informado por la impresora: " + message : "Error informado por la impresora.");
                    changed(jobRepository.save(j));
                });
    }

    private void complete(PrintJob j, Double actualHours, Double actualGrams, String notes, User actor, boolean fromTelemetry) {
        Instant now = Instant.now();
        if (j.getStartedAt() == null) j.setStartedAt(now);
        j.setFinishedAt(now);
        j.setStatus(PrintJobStatus.DONE);
        double hours = actualHours != null ? actualHours : nz(j.getEstimatedHours());
        double grams = actualGrams != null ? actualGrams : nz(j.getFilamentGrams());
        j.setActualHours(round2(hours));
        j.setActualGrams(round2(grams));
        appendNote(j, notes);

        Printer p = j.getPrinter();
        if (!fromTelemetry && p != null && !agentReporting(p)) {
            usageService.recordUsage(p, hours, grams, j.getFilamentType(), actor,
                    "trabajo #" + j.getId());
        }
        spoolService.consume(j.getSpool(), grams);
    }

    private boolean agentReporting(Printer p) {
        return integrationRepository.findByPrinterId(p.getId())
                .map(i -> i.getLastSeenAt() != null && i.getLastSeenAt().isAfter(Instant.now().minus(AGENT_ACTIVE_WINDOW)))
                .orElse(false);
    }

    private static void markStarted(PrintJob j) {
        j.setStatus(PrintJobStatus.PRINTING);
        j.setStartedAt(Instant.now());
    }

    private void apply(PrintJob j, PrintJobRequest r) {
        j.setTitle(truncate(r.title().trim(), 255));
        if (r.priority() != null) j.setPriority(r.priority());
        if (r.units() != null) j.setUnits(r.units());
        if (r.estimatedHours() != null) j.setEstimatedHours(r.estimatedHours());
        if (r.filamentGrams() != null) j.setFilamentGrams(r.filamentGrams());
        if (r.filamentType() != null) j.setFilamentType(r.filamentType());
        j.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        j.setPrinter(r.printerId() != null
                ? printerRepository.findByIdAndWorkspaceId(r.printerId(), wsId())
                        .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada"))
                : null);
        j.setSpool(r.spoolId() != null
                ? spoolRepository.findByIdAndWorkspaceId(r.spoolId(), wsId())
                        .orElseThrow(() -> new NoSuchElementException("Bobina no encontrada"))
                : null);
    }

    private PrintJob find(Long id) {
        return jobRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Trabajo no encontrado"));
    }

    // Avisa a los clientes del taller que la cola cambió.
    private PrintJobDTO changed(PrintJob j) {
        liveUpdates.toWorkspace(j.getWorkspaceId(), "jobs", Map.of("jobId", j.getId(), "status", j.getStatus().name()));
        return toDTO(j);
    }

    private static void appendNote(PrintJob j, String note) {
        if (note == null || note.isBlank()) return;
        j.setNotes(j.getNotes() == null ? note.trim() : j.getNotes() + "\n" + note.trim());
    }

    static PrintJobDTO toDTO(PrintJob j) {
        Printer p = j.getPrinter();
        FilamentSpool s = j.getSpool();
        Quote q = j.getQuote();
        return new PrintJobDTO(j.getId(), j.getTitle(), j.getStatus().name(), j.getPriority(), j.getUnits(),
                nz(j.getEstimatedHours()), nz(j.getFilamentGrams()),
                j.getFilamentType() != null ? j.getFilamentType().name() : null,
                j.getActualHours(), j.getActualGrams(), j.getNotes(),
                p != null ? p.getId() : null, p != null ? (p.getName() != null ? p.getName() : p.getModel()) : null,
                s != null ? s.getId() : null, s != null ? FilamentSpoolService.label(s) : null,
                q != null ? q.getId() : null, q != null ? q.getQuoteNumber() : null, q != null ? q.getClientName() : null,
                j.getCreatedAt(), j.getStartedAt(), j.getFinishedAt());
    }

    private static double nz(Double d) {
        return d != null ? d : 0.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
