// src/main/java/com/printops/demo/service/QuoteService.java
package com.printops.demo.service;

import com.printops.demo.security.TenantContext;
import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class QuoteService {

    // Jackson 3 (Spring Boot 4). Instancia local para (de)serializar el JSON de desgaste.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QuoteRepository quoteRepository;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private final NotificationService notificationService;
    private final QuotePdfService quotePdfService;
    private final WearCalculator wearCalculator;
    private final PrintJobService printJobService;
    private final FilamentSpoolRepository spoolRepository;
    private final LiveUpdates liveUpdates;
    private final String frontendUrl;

    public QuoteService(QuoteRepository quoteRepository,
                        PrinterRepository printerRepository,
                        UserRepository userRepository,
                        NotificationService notificationService,
                        QuotePdfService quotePdfService,
                        WearCalculator wearCalculator,
                        PrintJobService printJobService,
                        FilamentSpoolRepository spoolRepository,
                        LiveUpdates liveUpdates,
                        @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.quoteRepository = quoteRepository;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.quotePdfService = quotePdfService;
        this.wearCalculator = wearCalculator;
        this.printJobService = printJobService;
        this.spoolRepository = spoolRepository;
        this.liveUpdates = liveUpdates;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    // ── Cálculo en tiempo real (sin persistir) ──────────────────────────────
    @Transactional(readOnly = true)
    public QuoteResultsDTO calculate(QuoteCalculateRequest req) {
        CreateQuoteRequest r = toCreate(req);
        Printer printer = findPrinter(r.printerId());
        Costs c = computeCosts(r);
        double totalHours = r.printingHours() * units(r.units());
        ComponentWearDTO wear = wearCalculator.calculate(totalHours, r.filamentType(), printer);
        return new QuoteResultsDTO(
                c.filamentCost(), c.energyCost(), c.designCost(), c.operatorCost(),
                c.totalCost(), c.unitPrice(), c.totalPrice(), c.totalProfit(), c.roi(),
                wear, c.extras());
    }

    // ── CRUD ────────────────────────────────────────────────────────────────
    @Transactional
    public QuoteResponseDTO create(CreateQuoteRequest r, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        Quote q = new Quote();
        q.setCreatedBy(user);
        q.setWorkspaceId(wsId());
        q.setStatus(QuoteStatus.DRAFT);
        applyInputs(q, r);

        q = quoteRepository.saveAndFlush(q);
        q.setQuoteNumber("PRE-" + String.format("%04d", q.getId()));
        return toResponse(quoteRepository.saveAndFlush(q));
    }

    @Transactional(readOnly = true)
    public List<QuoteResponseDTO> list(QuoteStatus status, String clientName,
                                       LocalDate from, LocalDate to, Long printerId) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : LocalDateTime.of(9999, 12, 31, 23, 59, 59);
        return quoteRepository.search(wsId(), status, blankToNull(clientName), printerId, fromDt, toDt)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteResponseDTO> page(QuoteStatus status, String clientName, LocalDate from, LocalDate to,
                                               Long printerId, Integer page, Integer size) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime toDt = to != null ? to.atTime(LocalTime.MAX) : LocalDateTime.of(9999, 12, 31, 23, 59, 59);
        return PageResponse.of(quoteRepository.searchPage(wsId(), status, blankToNull(clientName), printerId,
                fromDt, toDt, PageResponse.request(page, size)), this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuoteResponseDTO get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public QuoteResponseDTO update(Long id, CreateQuoteRequest r) {
        Quote q = find(id);
        if (q.getStatus() != QuoteStatus.DRAFT) {
            throw new IllegalArgumentException("Solo se puede editar un presupuesto en estado DRAFT.");
        }
        applyInputs(q, r);
        return toResponse(quoteRepository.save(q));
    }

    @Transactional
    public QuoteResponseDTO changeStatus(Long id, QuoteStatus to) {
        Quote q = find(id);
        QuoteStatus from = q.getStatus();
        validateTransition(from, to);
        applyTransition(q, to, q.getCreatedBy());
        return toResponse(quoteRepository.save(q));
    }

    @Transactional
    public QuoteResponseDTO duplicate(Long id) {
        Quote orig = find(id);
        CreateQuoteRequest r = new CreateQuoteRequest(
                orig.getFilamentGrams(), orig.getFilamentPricePerGram(), orig.getFilamentType(),
                orig.getPrinterWatts(), orig.getEnergyPriceKwh(), orig.getPrintingHours(),
                orig.getDesignHours(), orig.getDesignHourlyRate(), orig.getOperatorHourlyRate(),
                orig.getMarginPercent(), orig.getDiscountPercent(), orig.getUnits(),
                orig.getPrinter() != null ? orig.getPrinter().getId() : null,
                orig.getClientName(), orig.getJobDescription(),
                orig.getSpool() != null ? orig.getSpool().getId() : null,
                orig.getExtraCosts().stream().map(e -> new ExtraCostItem(e.getDescription(), e.getAmount())).toList(),
                orig.isDraftWatermark());

        Quote q = new Quote();
        q.setCreatedBy(orig.getCreatedBy());
        q.setWorkspaceId(orig.getWorkspaceId());
        q.setStatus(QuoteStatus.DRAFT);
        applyInputs(q, r);

        q = quoteRepository.saveAndFlush(q);
        q.setQuoteNumber("PRE-" + String.format("%04d", q.getId()));
        return toResponse(quoteRepository.saveAndFlush(q));
    }

    @Transactional
    public void delete(Long id) {
        Quote q = find(id);
        if (q.getStatus() != QuoteStatus.DRAFT) {
            throw new IllegalArgumentException("Solo se puede eliminar un presupuesto en estado DRAFT.");
        }
        quoteRepository.delete(q);
    }

    // ── PDF ─────────────────────────────────────────────────────────────────
    // Se genera en cada pedido (es barato y siempre refleja el presupuesto actual). Antes se
    // guardaba una copia en uploads/, que en Railway se perdía en cada deploy.
    @Transactional(readOnly = true)
    public byte[] generatePdf(Long id) {
        return quotePdfService.generatePdf(find(id));
    }

    // ── Motor de cálculo ────────────────────────────────────────────────────
    private Costs computeCosts(CreateQuoteRequest r) {
        double filamentCost = r.filamentGrams() * r.filamentPricePerGram();
        double energyCost = (r.printerWatts() / 1000.0) * r.printingHours() * r.energyPriceKwh();
        double designCost = nz(r.designHours()) * nz(r.designHourlyRate());
        double operatorCost = r.printingHours() * nz(r.operatorHourlyRate());

        double totalCostUnit = filamentCost + energyCost + designCost + operatorCost;
        double unitPrice = totalCostUnit * (1 + r.marginPercent() / 100.0);
        double discount = unitPrice * nz(r.discountPercent()) / 100.0;
        double finalUnitPrice = unitPrice - discount;

        // Gastos extra (rodamientos, envío, insertos...): se trasladan a costo, sin margen ni
        // descuento, así que suman igual al costo y al precio y no cambian la ganancia.
        double extras = extrasTotal(r.extraCosts());

        int units = units(r.units());
        double totalPrice = finalUnitPrice * units + extras;
        double totalCost = totalCostUnit * units + extras;
        double totalProfit = totalPrice - totalCost;
        double roi = totalCost != 0 ? (totalProfit / totalCost) * 100.0 : 0.0;

        return new Costs(round2(filamentCost), round2(energyCost), round2(designCost),
                round2(operatorCost), round2(totalCost), round2(unitPrice),
                round2(totalPrice), round2(totalProfit), round2(roi), round2(extras));
    }

    static double extrasTotal(List<ExtraCostItem> items) {
        if (items == null) return 0.0;
        return items.stream().filter(i -> i != null && i.amount() != null).mapToDouble(ExtraCostItem::amount).sum();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private void applyInputs(Quote q, CreateQuoteRequest r) {
        q.setFilamentGrams(r.filamentGrams());
        q.setFilamentPricePerGram(r.filamentPricePerGram());
        q.setFilamentType(r.filamentType());
        q.setPrinterWatts(r.printerWatts());
        q.setEnergyPriceKwh(r.energyPriceKwh());
        q.setPrintingHours(r.printingHours());
        q.setDesignHours(nz(r.designHours()));
        q.setDesignHourlyRate(nz(r.designHourlyRate()));
        q.setOperatorHourlyRate(nz(r.operatorHourlyRate()));
        q.setMarginPercent(r.marginPercent());
        q.setDiscountPercent(nz(r.discountPercent()));
        q.setUnits(units(r.units()));
        q.setClientName(blankToNull(r.clientName()));
        q.setJobDescription(blankToNull(r.jobDescription()));

        q.setPrinter(findPrinter(r.printerId()));
        q.setSpool(r.spoolId() != null
                ? spoolRepository.findByIdAndWorkspaceId(r.spoolId(), wsId()).orElse(null)
                : null);

        Costs c = computeCosts(r);
        q.setFilamentCost(c.filamentCost());
        q.setEnergyCost(c.energyCost());
        q.setDesignCost(c.designCost());
        q.setOperatorCost(c.operatorCost());
        q.setTotalCost(c.totalCost());
        q.setUnitPrice(c.unitPrice());
        q.setTotalPrice(c.totalPrice());
        q.setTotalProfit(c.totalProfit());
        q.setRoi(c.roi());
        q.setExtraCostsTotal(c.extras());

        q.getExtraCosts().clear();
        if (r.extraCosts() != null) {
            int pos = 0;
            for (ExtraCostItem item : r.extraCosts()) {
                if (item == null || item.description() == null || item.description().isBlank()) continue;
                QuoteExtraCost e = new QuoteExtraCost();
                e.setQuote(q);
                e.setDescription(item.description().trim());
                e.setAmount(round2(item.amount() != null ? item.amount() : 0.0));
                e.setPosition(pos++);
                q.getExtraCosts().add(e);
            }
        }
        if (r.draftWatermark() != null) q.setDraftWatermark(r.draftWatermark());

        double totalHours = r.printingHours() * units(r.units());
        q.setComponentWear(toJson(wearCalculator.calculate(totalHours, r.filamentType(), q.getPrinter())));
    }

    // Efectos de un cambio de estado. Aceptado → entra a la cola de trabajos de impresión.
    private void applyTransition(Quote q, QuoteStatus to, User actor) {
        q.setStatus(to);
        if (to == QuoteStatus.ACCEPTED) {
            printJobService.createFromQuote(q, actor);
        }
    }

    // ── Link público para el cliente ────────────────────────────────────────
    // Genera (o reutiliza) el token del link. Compartir un borrador lo marca como enviado.
    @Transactional
    public QuoteResponseDTO share(Long id) {
        Quote q = find(id);
        if (q.getStatus() == QuoteStatus.REJECTED) {
            throw new IllegalArgumentException("Un presupuesto rechazado no se puede compartir.");
        }
        if (q.getStatus() == QuoteStatus.DRAFT) {
            applyTransition(q, QuoteStatus.SENT, q.getCreatedBy());
        }
        if (q.getPublicToken() == null) {
            byte[] bytes = new byte[24];
            RANDOM.nextBytes(bytes);
            q.setPublicToken(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            q.setSharedAt(java.time.Instant.now());
        }
        return toResponse(quoteRepository.save(q));
    }

    @Transactional
    public QuoteResponseDTO unshare(Long id) {
        Quote q = find(id);
        q.setPublicToken(null);
        q.setSharedAt(null);
        return toResponse(quoteRepository.save(q));
    }

    @Transactional(readOnly = true)
    public Quote findByPublicToken(String token) {
        if (token == null || token.length() < 16) throw new NoSuchElementException("Presupuesto no encontrado");
        return quoteRepository.findByPublicToken(token)
                .orElseThrow(() -> new NoSuchElementException("Presupuesto no encontrado"));
    }

    @Transactional(readOnly = true)
    public byte[] publicPdf(String token) {
        return quotePdfService.generatePdf(findByPublicToken(token));
    }

    // Decisión del cliente desde el link público. Solo sobre presupuestos enviados.
    @Transactional
    public Quote decideFromPublicLink(String token, boolean accept, String clientName, String note) {
        Quote q = findByPublicToken(token);
        if (q.getStatus() != QuoteStatus.SENT) {
            throw new IllegalArgumentException("Este presupuesto ya no admite cambios.");
        }
        QuoteStatus to = accept ? QuoteStatus.ACCEPTED : QuoteStatus.REJECTED;
        applyTransition(q, to, q.getCreatedBy());
        q.setClientDecisionAt(java.time.Instant.now());
        String who = blankToNull(clientName);
        String text = blankToNull(note);
        String decisionNote = who == null ? text : (text == null ? who : who + ": " + text);
        q.setClientDecisionNote(decisionNote != null && decisionNote.length() > 1000 ? decisionNote.substring(0, 1000) : decisionNote);
        quoteRepository.save(q);

        String msg = "El cliente " + (accept ? "aceptó" : "rechazó") + " el presupuesto " + q.getQuoteNumber()
                + (q.getClientName() != null ? " (" + q.getClientName() + ")" : "") + ".";
        java.util.Set<Long> recipients = new java.util.LinkedHashSet<>();
        if (q.getCreatedBy() != null) recipients.add(q.getCreatedBy().getId());
        userRepository.findByRoleAndWorkspaceId(Role.MANAGER, q.getWorkspaceId()).forEach(u -> recipients.add(u.getId()));
        for (Long r : recipients) {
            notificationService.createFor(r, accept ? "QUOTE_ACCEPTED" : "QUOTE_REJECTED", msg, null);
            liveUpdates.toUser(r, "notification", java.util.Map.of(
                    "title", accept ? "Presupuesto aceptado" : "Presupuesto rechazado",
                    "body", msg, "quoteId", q.getId()));
        }
        return q;
    }

    public String publicUrl(String token) {
        return frontendUrl + "/q/" + token;
    }

    private void validateTransition(QuoteStatus from, QuoteStatus to) {
        boolean allowed = switch (from) {
            // Aceptado directo: el cliente confirma sin que se envíe el presupuesto.
            case DRAFT -> to == QuoteStatus.SENT || to == QuoteStatus.ACCEPTED;
            case SENT -> to == QuoteStatus.ACCEPTED || to == QuoteStatus.REJECTED;
            case ACCEPTED -> to == QuoteStatus.PAID;
            case REJECTED, PAID -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Transición no permitida de " + from + " a " + to + ".");
        }
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // Solo impresoras del workspace actual; un id ajeno se ignora.
    private Printer findPrinter(Long printerId) {
        return printerId != null
                ? printerRepository.findByIdAndWorkspaceId(printerId, wsId()).orElse(null)
                : null;
    }

    private Quote find(Long id) {
        return quoteRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Presupuesto no encontrado con id " + id));
    }

    private QuoteResponseDTO toResponse(Quote q) {
        String printerName = null;
        if (q.getPrinter() != null) {
            printerName = q.getPrinter().getName() != null ? q.getPrinter().getName() : q.getPrinter().getModel();
        }
        String pdfUrl = "/api/quotes/" + q.getId() + "/pdf";
        return new QuoteResponseDTO(
                q.getId(),
                q.getQuoteNumber(),
                q.getClientName(),
                q.getJobDescription(),
                q.getUnits(),
                q.getFilamentGrams(),
                q.getFilamentPricePerGram(),
                q.getFilamentType() != null ? q.getFilamentType().name() : null,
                q.getPrinterWatts(),
                q.getEnergyPriceKwh(),
                q.getPrintingHours(),
                q.getDesignHours(),
                q.getDesignHourlyRate(),
                q.getOperatorHourlyRate(),
                q.getMarginPercent(),
                q.getDiscountPercent(),
                q.getFilamentCost(),
                q.getEnergyCost(),
                q.getDesignCost(),
                q.getOperatorCost(),
                q.getTotalCost(),
                q.getUnitPrice(),
                q.getTotalPrice(),
                q.getTotalProfit(),
                q.getRoi(),
                fromJson(q.getComponentWear()),
                q.getPrinter() != null ? q.getPrinter().getId() : null,
                printerName,
                q.getStatus() != null ? q.getStatus().name() : null,
                q.getCreatedAt(),
                pdfUrl,
                q.getSpool() != null ? q.getSpool().getId() : null,
                q.getPublicToken() != null ? publicUrl(q.getPublicToken()) : null,
                q.getSharedAt(),
                q.getClientDecisionAt(),
                q.getClientDecisionNote(),
                q.getExtraCosts().stream().map(e -> new ExtraCostItem(e.getDescription(), e.getAmount())).toList(),
                q.getExtraCostsTotal(),
                q.isDraftWatermark()
        );
    }

    private CreateQuoteRequest toCreate(QuoteCalculateRequest r) {
        return new CreateQuoteRequest(
                r.filamentGrams(), r.filamentPricePerGram(), r.filamentType(),
                r.printerWatts(), r.energyPriceKwh(), r.printingHours(),
                r.designHours(), r.designHourlyRate(), r.operatorHourlyRate(),
                r.marginPercent(), r.discountPercent(), r.units(),
                r.printerId(), r.clientName(), r.jobDescription(), null, r.extraCosts(), null);
    }

    private String toJson(ComponentWearDTO wear) {
        try {
            return MAPPER.writeValueAsString(wear);
        } catch (Exception e) {
            return null;
        }
    }

    private ComponentWearDTO fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, ComponentWearDTO.class);
        } catch (Exception e) {
            return null;
        }
    }

    private int units(Integer units) {
        return units != null ? units : 1;
    }

    private double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private record Costs(double filamentCost, double energyCost, double designCost, double operatorCost,
                         double totalCost, double unitPrice, double totalPrice, double totalProfit, double roi,
                         double extras) {}
}
