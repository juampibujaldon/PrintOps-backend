// src/main/java/com/printops/demo/service/QuoteService.java
package com.printops.demo.service;

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
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class QuoteService {

    // Jackson 3 (Spring Boot 4). Instancia local para (de)serializar el JSON de desgaste.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final QuoteRepository quoteRepository;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final StatusHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final QuotePdfService quotePdfService;

    public QuoteService(QuoteRepository quoteRepository,
                        PrinterRepository printerRepository,
                        UserRepository userRepository,
                        MaintenanceOrderRepository orderRepository,
                        StatusHistoryRepository historyRepository,
                        NotificationService notificationService,
                        QuotePdfService quotePdfService) {
        this.quoteRepository = quoteRepository;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.quotePdfService = quotePdfService;
    }

    // ── Cálculo en tiempo real (sin persistir) ──────────────────────────────
    @Transactional(readOnly = true)
    public QuoteResultsDTO calculate(QuoteCalculateRequest req) {
        CreateQuoteRequest r = toCreate(req);
        Printer printer = r.printerId() != null ? printerRepository.findById(r.printerId()).orElse(null) : null;
        Costs c = computeCosts(r);
        double totalHours = r.printingHours() * units(r.units());
        ComponentWearDTO wear = calculateWear(totalHours, r.filamentType(), printer);
        return new QuoteResultsDTO(
                c.filamentCost(), c.energyCost(), c.designCost(), c.operatorCost(),
                c.totalCost(), c.unitPrice(), c.totalPrice(), c.totalProfit(), c.roi(),
                wear);
    }

    // ── CRUD ────────────────────────────────────────────────────────────────
    @Transactional
    public QuoteResponseDTO create(CreateQuoteRequest r, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
        Quote q = new Quote();
        q.setCreatedBy(user);
        q.setStatus(QuoteStatus.DRAFT);
        applyInputs(q, r);

        q = quoteRepository.saveAndFlush(q);
        q.setQuoteNumber("PRE-" + String.format("%04d", q.getId()));
        return toResponse(quoteRepository.saveAndFlush(q));
    }

    @Transactional(readOnly = true)
    public List<QuoteResponseDTO> list(QuoteStatus status, String clientName,
                                       LocalDate from, LocalDate to, Long printerId) {
        LocalDateTime fromDt = from != null ? from.atStartOfDay() : null;
        LocalDateTime toDt = to != null ? to.atTime(23, 59, 59) : null;
        return quoteRepository.search(status, blankToNull(clientName), printerId, fromDt, toDt)
                .stream().map(this::toResponse).toList();
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
        // Al salir de DRAFT se aplica el desgaste real sobre la impresora.
        if (from == QuoteStatus.DRAFT && to == QuoteStatus.SENT) {
            applyWear(q);
        }
        q.setStatus(to);
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
                orig.getClientName(), orig.getJobDescription());

        Quote q = new Quote();
        q.setCreatedBy(orig.getCreatedBy());
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
    @Transactional
    public byte[] generateAndStorePdf(Long id) {
        Quote q = find(id);
        byte[] pdf = quotePdfService.generatePdf(q);
        String fileName = "PRE-" + q.getId() + ".pdf";
        try {
            Path dir = Paths.get("uploads/quotes");
            Files.createDirectories(dir);
            Files.write(dir.resolve(fileName), pdf);
            q.setPdfPath("uploads/quotes/" + fileName);
            quoteRepository.save(q);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el PDF del presupuesto.", e);
        }
        return pdf;
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

        int units = units(r.units());
        double totalPrice = finalUnitPrice * units;
        double totalCost = totalCostUnit * units;
        double totalProfit = totalPrice - totalCost;
        double roi = totalCost != 0 ? (totalProfit / totalCost) * 100.0 : 0.0;

        return new Costs(round2(filamentCost), round2(energyCost), round2(designCost),
                round2(operatorCost), round2(totalCost), round2(unitPrice),
                round2(totalPrice), round2(totalProfit), round2(roi));
    }

    // Vida útil base por componente (horas). La boquilla depende del filamento.
    public ComponentWearDTO calculateWear(double totalHours, FilamentType type, Printer printer) {
        double prev = printer != null && printer.getTotalPrintingHours() != null
                ? printer.getTotalPrintingHours() : 0.0;
        double hoursAfter = prev + totalHours;

        return new ComponentWearDTO(
                componentStatus(nozzleLifespan(type), hoursAfter),
                componentStatus(800.0, hoursAfter),
                componentStatus(1500.0, hoursAfter),
                componentStatus(2000.0, hoursAfter),
                componentStatus(3000.0, hoursAfter));
    }

    private double nozzleLifespan(FilamentType type) {
        return switch (type) {
            case PLA, PETG -> 500.0;
            case ABS, ASA -> 400.0;
            case TPU -> 300.0;
            case OTHER -> 200.0;
        };
    }

    private ComponentWearDTO.ComponentStatus componentStatus(double lifespan, double hoursAfter) {
        double percent = (hoursAfter / lifespan) * 100.0;
        double remaining = Math.max(0.0, lifespan - hoursAfter);
        boolean critical = percent >= 90.0;
        return new ComponentWearDTO.ComponentStatus(round2(percent), round2(hoursAfter), round2(remaining), critical);
    }

    // ── Desgaste real al confirmar ──────────────────────────────────────────
    private void applyWear(Quote q) {
        Printer printer = q.getPrinter();
        if (printer == null) return;

        double delta = (q.getPrintingHours() != null ? q.getPrintingHours() : 0.0)
                * (q.getUnits() != null ? q.getUnits() : 1);
        ComponentWearDTO wear = calculateWear(delta, q.getFilamentType(), printer);

        double prev = printer.getTotalPrintingHours() != null ? printer.getTotalPrintingHours() : 0.0;
        printer.setTotalPrintingHours(prev + delta);
        printerRepository.save(printer);

        handleWearAlerts(q, printer, wear);
    }

    private void handleWearAlerts(Quote q, Printer printer, ComponentWearDTO wear) {
        ComponentWearDTO.ComponentStatus[] comps = {
                wear.nozzle(), wear.hotend(), wear.belts(), wear.bearings(), wear.heatbed()};
        String[] names = {"boquilla", "hotend", "correas", "rodamientos", "cama caliente"};
        Long recipient = q.getCreatedBy() != null ? q.getCreatedBy().getId() : null;

        for (int i = 0; i < comps.length; i++) {
            ComponentWearDTO.ComponentStatus c = comps[i];
            if (c.percent() > 100.0) {
                createCorrectiveOrder(q, printer, names[i], recipient);
            } else if (c.percent() >= 90.0) {
                notificationService.createFor(recipient, "WEAR_ALERT",
                        "La " + names[i] + " de la impresora " + printer.getSerialNumber()
                                + " superó el " + (int) c.percent() + "% de vida útil (presupuesto "
                                + q.getQuoteNumber() + ").", null);
            }
        }
    }

    private void createCorrectiveOrder(Quote q, Printer printer, String componentName, Long recipient) {
        MaintenanceOrder order = new MaintenanceOrder();
        order.setPrinter(printer);
        order.setAssignedTo(q.getCreatedBy());
        order.setType(OrderType.CORRECTIVE);
        order.setStatus(OrderStatus.PENDING);
        order.setDescription("Mantenimiento correctivo automático: " + componentName
                + " superó el 100% de vida útil (presupuesto " + q.getQuoteNumber() + ").");
        orderRepository.save(order);

        StatusHistory h = new StatusHistory();
        h.setOrderId(order.getId());
        h.setToStatus(OrderStatus.PENDING);
        h.setChangedBy(q.getCreatedBy());
        historyRepository.save(h);

        notificationService.createFor(recipient, "ORDER_CREATED",
                "Orden correctiva #" + order.getId() + " creada automáticamente por desgaste de "
                        + componentName + " en " + printer.getSerialNumber() + ".", order.getId());
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

        if (r.printerId() != null) {
            q.setPrinter(printerRepository.findById(r.printerId()).orElse(null));
        } else {
            q.setPrinter(null);
        }

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

        double totalHours = r.printingHours() * units(r.units());
        q.setComponentWear(toJson(calculateWear(totalHours, r.filamentType(), q.getPrinter())));
    }

    private void validateTransition(QuoteStatus from, QuoteStatus to) {
        boolean allowed = switch (from) {
            case DRAFT -> to == QuoteStatus.SENT;
            case SENT -> to == QuoteStatus.ACCEPTED || to == QuoteStatus.REJECTED;
            case ACCEPTED, REJECTED -> false;
        };
        if (!allowed) {
            throw new IllegalArgumentException("Transición no permitida de " + from + " a " + to + ".");
        }
    }

    private Quote find(Long id) {
        return quoteRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Presupuesto no encontrado con id " + id));
    }

    private QuoteResponseDTO toResponse(Quote q) {
        String printerName = null;
        if (q.getPrinter() != null) {
            printerName = q.getPrinter().getName() != null ? q.getPrinter().getName() : q.getPrinter().getModel();
        }
        String pdfUrl = null;
        if (q.getPdfPath() != null) {
            try {
                pdfUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/quotes/").path(String.valueOf(q.getId())).path("/pdf").toUriString();
            } catch (Exception ignored) {
                // Fuera de contexto de request (p. ej. tests): pdfUrl queda null.
            }
        }
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
                pdfUrl
        );
    }

    private CreateQuoteRequest toCreate(QuoteCalculateRequest r) {
        return new CreateQuoteRequest(
                r.filamentGrams(), r.filamentPricePerGram(), r.filamentType(),
                r.printerWatts(), r.energyPriceKwh(), r.printingHours(),
                r.designHours(), r.designHourlyRate(), r.operatorHourlyRate(),
                r.marginPercent(), r.discountPercent(), r.units(),
                r.printerId(), r.clientName(), r.jobDescription());
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
                         double totalCost, double unitPrice, double totalPrice, double totalProfit, double roi) {}
}
