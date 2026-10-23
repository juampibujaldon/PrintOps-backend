package com.printops.demo.features;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.*;
import com.printops.demo.service.*;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Funciones nuevas de punta a punta: cola de trabajos y uso real, bobinas, gastos extra,
// link público, telemetría del agente, compras, exportación, actividad y analítica.
class FeaturesIT extends IntegrationTestBase {

    @LocalServerPort int port;
    @Autowired QuoteService quoteService;
    @Autowired PrintJobService jobService;
    @Autowired FilamentSpoolService spoolService;
    @Autowired PrinterIntegrationService integrationService;
    @Autowired PurchaseOrderService purchaseOrderService;
    @Autowired ExportService exportService;
    @Autowired ActivityService activityService;
    @Autowired AnalyticsService analyticsService;
    @Autowired QuotePdfService pdfService;
    @Autowired WorkspaceService workspaceService;
    @Autowired QuoteRepository quoteRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired FilamentSpoolRepository spoolRepository;
    @Autowired StockMovementRepository movementRepository;
    @Autowired JwtService jwtService;

    private final HttpClient http = HttpClient.newHttpClient();

    private CreateQuoteRequest quote(Long printerId, Long spoolId, double grams, double hours, int units,
                                     List<ExtraCostItem> extras) {
        return new CreateQuoteRequest(grams, 0.05, FilamentType.PLA, 200.0, 0.1, hours, 0.0, 0.0, 10.0, 30.0, 0.0,
                units, printerId, "Cliente", "Soporte", spoolId, extras, null);
    }

    private HttpResponse<String> send(String method, String path, String body, String auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (auth != null) b.header("Authorization", auth);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(m.find()).as("campo %s en %s", name, json).isTrue();
        return m.group(1);
    }

    // ── Cola de trabajos, uso real y bobinas ─────────────────────────────────
    @Test
    void acceptedQuoteBecomesJob_finishingRecordsHoursAndGramsAndConsumesSpool() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P1");
        loginAs(w.manager);
        FilamentSpoolDTO spool = spoolService.create(new SpoolRequest(FilamentType.PLA, "Negro", "Grilon", 1000.0, null, 20000.0, null));
        assertThat(spool.pricePerGram()).isEqualTo(20.0);

        loginAs(w.tech);
        Long quoteId = quoteService.create(quote(printer.getId(), spool.id(), 100, 3, 2, null), w.tech.getEmail()).id();
        quoteService.changeStatus(quoteId, QuoteStatus.SENT);
        // Enviar ya no suma uso: el presupuesto puede rechazarse y nunca imprimirse.
        assertThat(printerRepository.findById(printer.getId()).orElseThrow().getTotalPrintingHours()).isZero();

        quoteService.changeStatus(quoteId, QuoteStatus.ACCEPTED);
        List<PrintJobDTO> queue = jobService.list(true, null);
        assertThat(queue).hasSize(1);
        PrintJobDTO job = queue.getFirst();
        assertThat(job.status()).isEqualTo("QUEUED");
        assertThat(job.estimatedHours()).isEqualTo(6.0);
        assertThat(job.filamentGrams()).isEqualTo(200.0);
        assertThat(job.spoolId()).isEqualTo(spool.id());

        // Aceptar de nuevo (idempotente vía PAID) no duplica el trabajo.
        quoteService.changeStatus(quoteId, QuoteStatus.PAID);
        assertThat(jobService.list(true, null)).hasSize(1);

        jobService.start(job.id());
        assertThatThrownBy(() -> jobService.start(job.id())).isInstanceOf(IllegalArgumentException.class);
        PrintJobDTO done = jobService.finish(job.id(), new FinishPrintJobRequest(5.5, 180.0, null), w.tech.getEmail());

        assertThat(done.status()).isEqualTo("DONE");
        Printer after = printerRepository.findById(printer.getId()).orElseThrow();
        assertThat(after.getTotalPrintingHours()).isEqualTo(5.5);
        // Antes del fix los gramos nunca se acumulaban (las reglas FILAMENT_GRAMS no se disparaban).
        assertThat(after.getTotalFilamentGrams()).isEqualTo(180.0);
        assertThat(spoolRepository.findById(spool.id()).orElseThrow().getRemainingGrams()).isEqualTo(820.0);
        assertThat(jobService.list(true, null)).isEmpty();
        assertThat(jobService.list(false, null)).extracting(PrintJobDTO::id).contains(job.id());
    }

    @Test
    void jobCannotStartWithoutPrinterOrOnBusyPrinter() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P1");
        loginAs(w.tech);
        PrintJobDTO noPrinter = jobService.create(new PrintJobRequest("Sin máquina", 0, 1, 1.0, 10.0, FilamentType.PLA, null, null, null), w.tech.getEmail());
        assertThatThrownBy(() -> jobService.start(noPrinter.id())).hasMessageContaining("impresora");

        PrintJobDTO a = jobService.create(new PrintJobRequest("A", 0, 1, 1.0, 10.0, FilamentType.PLA, printer.getId(), null, null), w.tech.getEmail());
        PrintJobDTO b = jobService.create(new PrintJobRequest("B", 5, 1, 1.0, 10.0, FilamentType.PLA, printer.getId(), null, null), w.tech.getEmail());
        // Mayor prioridad primero en la cola.
        assertThat(jobService.list(true, printer.getId()).stream().map(PrintJobDTO::title).toList())
                .containsSubsequence("B", "A");
        jobService.start(a.id());
        assertThatThrownBy(() -> jobService.start(b.id())).hasMessageContaining("en curso");
        jobService.fail(a.id(), "Se despegó");
        assertThat(jobService.requeue(a.id()).status()).isEqualTo("QUEUED");
    }

    // ── Gastos extra ─────────────────────────────────────────────────────────
    @Test
    void extraCostsAreChargedAtCostAndPersisted() {
        World w = newWorld();
        loginAs(w.tech);
        QuoteResultsDTO base = quoteService.calculate(calc(null));
        QuoteResultsDTO withExtras = quoteService.calculate(calc(List.of(new ExtraCostItem("Rodamiento", 4500.0),
                new ExtraCostItem("Envío", 1500.0))));

        assertThat(withExtras.extraCostsTotal()).isEqualTo(6000.0);
        assertThat(withExtras.totalPrice()).isEqualTo(base.totalPrice() + 6000.0);
        assertThat(withExtras.totalCost()).isEqualTo(base.totalCost() + 6000.0);
        // A costo: no cambian la ganancia.
        assertThat(withExtras.totalProfit()).isEqualTo(base.totalProfit());

        QuoteResponseDTO saved = quoteService.create(quote(null, null, 100, 3, 1,
                List.of(new ExtraCostItem("Rodamiento", 4500.0))), w.tech.getEmail());
        assertThat(saved.extraCosts()).containsExactly(new ExtraCostItem("Rodamiento", 4500.0));
        assertThat(saved.extraCostsTotal()).isEqualTo(4500.0);
        assertThat(saved.draftWatermark()).isTrue();

        QuoteResponseDTO copy = quoteService.duplicate(saved.id());
        assertThat(copy.extraCosts()).containsExactly(new ExtraCostItem("Rodamiento", 4500.0));

        QuoteResponseDTO updated = quoteService.update(saved.id(), new CreateQuoteRequest(100.0, 0.05, FilamentType.PLA,
                200.0, 0.1, 3.0, 0.0, 0.0, 10.0, 30.0, 0.0, 1, null, "Cliente", "Soporte", null, List.of(), false));
        assertThat(updated.extraCosts()).isEmpty();
        assertThat(updated.extraCostsTotal()).isZero();
        assertThat(updated.draftWatermark()).isFalse();
    }

    private QuoteCalculateRequest calc(List<ExtraCostItem> extras) {
        return new QuoteCalculateRequest(100.0, 0.05, FilamentType.PLA, 200.0, 0.1, 3.0, 0.0, 0.0, 10.0, 30.0, 10.0,
                2, null, null, null, extras);
    }

    // ── PDF: marca de agua opcional y logo ───────────────────────────────────
    @Test
    void pdfWatermarkIsOptionalAndLogoIsDrawn() throws Exception {
        World w = newWorld();
        Workspace ws = new Workspace();
        ws.setName("Taller Logo");
        ws = workspaceRepository.save(ws);
        User manager = newUser("logo", Role.MANAGER, ws.getId());
        loginAs(manager);

        Long id = quoteService.create(quote(null, null, 100, 3, 1, List.of(new ExtraCostItem("Rodamiento 608", 4500.0))),
                manager.getEmail()).id();
        String withMark = text(quoteService.generatePdf(id));
        assertThat(count(withMark, "BORRADOR")).isGreaterThanOrEqualTo(2); // pastilla + marca de agua
        assertThat(withMark).contains("Rodamiento 608");

        quoteService.update(id, new CreateQuoteRequest(100.0, 0.05, FilamentType.PLA, 200.0, 0.1, 3.0, 0.0, 0.0, 10.0,
                30.0, 0.0, 1, null, "Cliente", "Soporte", null, null, false));
        assertThat(count(text(quoteService.generatePdf(id)), "BORRADOR")).isEqualTo(1); // solo la pastilla

        assertThatThrownBy(() -> workspaceService.uploadLogo(manager.getEmail(),
                new MockMultipartFile("logo", "x.png", "image/png", "no soy una imagen".getBytes())))
                .hasMessageContaining("PNG o JPG");
        WorkspaceResponse withLogo = workspaceService.uploadLogo(manager.getEmail(),
                new MockMultipartFile("logo", "logo.png", "image/png", png()));
        assertThat(withLogo.logoUrl()).startsWith("/api/files/logos/");
        byte[] pdf = quoteService.generatePdf(id);
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("/Image");

        assertThat(workspaceService.deleteLogo(manager.getEmail()).logoUrl()).isNull();
        assertThat(w).isNotNull();
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }

    private static byte[] png() throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ── Link público del presupuesto ─────────────────────────────────────────
    @Test
    void publicLinkShowsQuoteWithoutInternalCostsAndLetsTheClientAccept() throws Exception {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P1");
        loginAs(w.tech);
        Long id = quoteService.create(quote(printer.getId(), null, 100, 3, 1, null), w.tech.getEmail()).id();

        QuoteResponseDTO shared = quoteService.share(id);
        assertThat(shared.status()).isEqualTo("SENT");
        assertThat(shared.publicUrl()).contains("/q/");
        String token = shared.publicUrl().substring(shared.publicUrl().lastIndexOf('/') + 1);
        assertThat(quoteService.share(id).publicUrl()).isEqualTo(shared.publicUrl()); // reutiliza el token

        HttpResponse<String> view = send("GET", "/api/public/quotes/" + token, null, null);
        assertThat(view.statusCode()).isEqualTo(200);
        assertThat(view.body()).contains("\"status\":\"SENT\"").contains("totalPrice")
                .doesNotContain("totalCost").doesNotContain("marginPercent").doesNotContain("totalProfit");
        assertThat(send("GET", "/api/public/quotes/inexistente-0000000000", null, null).statusCode()).isEqualTo(404);

        HttpResponse<String> accept = send("POST", "/api/public/quotes/" + token + "/accept",
                "{\"name\":\"Ana\",\"note\":\"Dale\"}", null);
        assertThat(accept.statusCode()).isEqualTo(200);
        assertThat(accept.body()).contains("\"status\":\"ACCEPTED\"");
        assertThat(quoteRepository.findById(id).orElseThrow().getClientDecisionNote()).isEqualTo("Ana: Dale");
        assertThat(jobService.list(true, null)).hasSize(1);
        assertThat(send("POST", "/api/public/quotes/" + token + "/reject", null, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/public/quotes/" + token + "/pdf", null, null).statusCode()).isEqualTo(200);

        quoteService.unshare(id);
        assertThat(send("GET", "/api/public/quotes/" + token, null, null).statusCode()).isEqualTo(404);
    }

    // ── Telemetría del agente local ──────────────────────────────────────────
    @Test
    void agentTelemetryStartsAndFinishesJobsAndRecordsRealUsage() throws Exception {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "Bambu");
        loginAs(w.tech);
        PrintJobDTO job = jobService.create(new PrintJobRequest("Engranaje", 0, 1, 2.0, 50.0, FilamentType.PETG,
                printer.getId(), null, null), w.tech.getEmail());

        loginAs(w.manager);
        IntegrationDTO integration = integrationService.createOrRotate(printer.getId(), IntegrationType.BAMBU);
        assertThat(integration.token()).startsWith("pod_");
        assertThat(integrationService.get(printer.getId()).orElseThrow().token()).isNull(); // se muestra una sola vez

        String device = "Device " + integration.token();
        assertThat(send("POST", "/api/agent/telemetry", "{\"state\":\"PRINTING\"}", "Device pod_falso").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/agent/telemetry", "{\"state\":\"PRINTING\"}", null).statusCode()).isEqualTo(401);

        HttpResponse<String> printing = send("POST", "/api/agent/telemetry",
                "{\"state\":\"PRINTING\",\"progress\":12.5,\"nozzleTemp\":245,\"bedTemp\":80,\"jobName\":\"gear.3mf\",\"remainingSeconds\":3600}", device);
        assertThat(printing.statusCode()).isEqualTo(200);
        loginAs(w.tech);
        assertThat(jobService.get(job.id()).status()).isEqualTo("PRINTING");

        send("POST", "/api/agent/telemetry",
                "{\"state\":\"FINISHED\",\"progress\":100,\"usage\":{\"printSeconds\":5400,\"filamentGrams\":42.5}}", device);
        PrintJobDTO done = jobService.get(job.id());
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.actualHours()).isEqualTo(1.5);
        Printer after = printerRepository.findById(printer.getId()).orElseThrow();
        assertThat(after.getTotalPrintingHours()).isEqualTo(1.5);
        assertThat(after.getTotalFilamentGrams()).isEqualTo(42.5);

        List<PrinterTelemetryDTO> live = integrationService.telemetry();
        assertThat(live).singleElement().satisfies(t -> {
            assertThat(t.state()).isEqualTo("FINISHED");
            assertThat(t.online()).isTrue();
        });

        // Con el agente reportando, terminar un trabajo a mano no vuelve a sumar uso.
        PrintJobDTO manual = jobService.create(new PrintJobRequest("Manual", 0, 1, 3.0, 30.0, FilamentType.PLA,
                printer.getId(), null, null), w.tech.getEmail());
        jobService.start(manual.id());
        jobService.finish(manual.id(), null, w.tech.getEmail());
        assertThat(printerRepository.findById(printer.getId()).orElseThrow().getTotalPrintingHours()).isEqualTo(1.5);

        // Rotar invalida el token anterior.
        loginAs(w.manager);
        integrationService.createOrRotate(printer.getId(), IntegrationType.BAMBU);
        assertThat(send("POST", "/api/agent/telemetry", "{\"state\":\"IDLE\"}", device).statusCode()).isEqualTo(401);
    }

    @Test
    void integrationEndpointsAreManagerOnly() throws Exception {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P");
        String tech = "Bearer " + jwtService.generateAccessToken(w.tech.getEmail(), "TECNICO", w.workspaceId);
        String manager = "Bearer " + jwtService.generateAccessToken(w.manager.getEmail(), "MANAGER", w.workspaceId);
        assertThat(send("POST", "/api/printers/" + printer.getId() + "/integration", "{\"type\":\"OCTOPRINT\"}", tech).statusCode()).isEqualTo(403);
        HttpResponse<String> ok = send("POST", "/api/printers/" + printer.getId() + "/integration", "{\"type\":\"OCTOPRINT\"}", manager);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(field(ok.body(), "token")).startsWith("pod_");
        assertThat(send("GET", "/api/suppliers", null, tech).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/activity", null, tech).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/jobs", null, tech).statusCode()).isEqualTo(200);
    }

    // ── Compras ──────────────────────────────────────────────────────────────
    @Test
    void lowStockSuggestionsBecomeOrdersAndReceivingAddsStock() {
        World w = newWorld();
        addParts(w, 8); // índices 0 y 4 en stock bajo (stock 1, mínimo 2)
        loginAs(w.manager);
        List<PurchaseOrderDTO> suggested = purchaseOrderService.suggest(w.manager.getEmail());
        assertThat(suggested).hasSize(1);
        PurchaseOrderDTO po = suggested.getFirst();
        assertThat(po.number()).startsWith("OC-");
        assertThat(po.lines()).hasSize(2).allSatisfy(l -> assertThat(l.quantity()).isEqualTo(3)); // 2*2 - 1
        // No se sugiere dos veces lo que ya está pedido.
        assertThat(purchaseOrderService.suggest(w.manager.getEmail())).isEmpty();

        purchaseOrderService.send(po.id());
        PurchaseOrderDTO received = purchaseOrderService.receive(po.id(), w.manager.getEmail());
        assertThat(received.status()).isEqualTo("RECEIVED");
        SparePart part = sparePartRepository.findById(w.parts.get(0).getId()).orElseThrow();
        assertThat(part.getStock()).isEqualTo(4);
        assertThat(movementRepository.findAll()).anyMatch(m -> m.getSparePart().getId().equals(part.getId())
                && m.getType() == MovementType.PURCHASE && m.getQuantityChange() == 3);
        assertThatThrownBy(() -> purchaseOrderService.receive(po.id(), w.manager.getEmail()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Exportación, actividad y analítica ───────────────────────────────────
    @Test
    void csvExportIsExcelFriendlyAndEscapesFormulas() {
        World w = newWorld();
        SparePart evil = new SparePart();
        evil.setName("=HYPERLINK(\"http://x\")");
        evil.setPartNumber("EV-1");
        evil.setStock(2);
        evil.setMinStock(1);
        evil.setUnitPrice(1.5);
        evil.setWorkspaceId(w.workspaceId);
        sparePartRepository.save(evil);
        loginAs(w.manager);

        String csv = new String(exportService.export("parts", null, null), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Repuesto;Número de parte");
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://x\"\")\"").contains(";1,5;");
        assertThat(new String(exportService.export("orders", LocalDate.now().minusDays(1), LocalDate.now()), StandardCharsets.UTF_8))
                .startsWith("﻿Orden;Fecha");
    }

    @Test
    void activityLogMergesOrderStockAndJobEvents() {
        World w = newWorld();
        addParts(w, 4);
        addOrders(w, 1, 2);
        loginAs(w.tech);
        jobService.create(new PrintJobRequest("Pieza", 0, 1, 1.0, 1.0, FilamentType.PLA, null, null, null), w.tech.getEmail());
        loginAs(w.manager);
        purchaseOrderService.receive(purchaseOrderService.suggest(w.manager.getEmail()).getFirst().id(), w.manager.getEmail());

        PageResponse<ActivityItemDTO> page = activityService.page(null, 0, 50);
        assertThat(page.content()).extracting(ActivityItemDTO::kind).contains("ORDER_STATUS", "STOCK", "JOB");
        assertThat(activityService.page("STOCK", 0, 50).content()).allMatch(i -> i.kind().equals("STOCK"));
        assertThat(activityService.page(null, 0, 1).content()).hasSize(1);
    }

    @Test
    void predictionsUseMtbfAndCanSchedulePreventive() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "Vieja");
        Instant now = Instant.now();
        // Correctivas cada 10 días; la última hace 9 días: riesgo 90%.
        for (int daysAgo : new int[]{29, 19, 9}) {
            MaintenanceOrder o = new MaintenanceOrder();
            o.setPrinter(printer);
            o.setType(OrderType.CORRECTIVE);
            o.setStatus(OrderStatus.COMPLETED);
            o.setWorkspaceId(w.workspaceId);
            o = orderRepository.save(o);
            jdbc.update("UPDATE maintenance_orders SET created_at = ? WHERE id = ?",
                    java.sql.Timestamp.from(now.minus(daysAgo, ChronoUnit.DAYS)), o.getId());
        }
        loginAs(w.manager);
        FailurePredictionDTO p = analyticsService.predictions().stream()
                .filter(d -> d.printerId().equals(printer.getId())).findFirst().orElseThrow();
        assertThat(p.correctiveCount()).isEqualTo(3);
        assertThat(p.mtbfDays()).isEqualTo(10.0);
        assertThat(p.riskPercent()).isBetween(80.0, 100.0);
        assertThat(p.recommendation()).contains("preventivo");

        analyticsService.schedulePreventive(printer.getId(), w.manager.getEmail());
        FailurePredictionDTO after = analyticsService.predictions().stream()
                .filter(d -> d.printerId().equals(printer.getId())).findFirst().orElseThrow();
        assertThat(after.hasOpenPreventive()).isTrue();
        assertThat(after.recommendation()).isEqualTo("Preventivo ya programado.");
    }

    @Test
    void profitabilityComparesRevenueWithProductionAndMaintenanceCosts() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "Rentable");
        loginAs(w.tech);
        QuoteResponseDTO q = quoteService.create(quote(printer.getId(), null, 100, 3, 2, null), w.tech.getEmail());
        quoteService.changeStatus(q.id(), QuoteStatus.ACCEPTED);
        loginAs(w.manager);

        ProfitabilityDTO r = analyticsService.profitability(LocalDate.now().minusDays(1), LocalDate.now());
        ProfitabilityDTO.Row row = r.printers().stream().filter(x -> x.printerId().equals(printer.getId())).findFirst().orElseThrow();
        assertThat(row.quotes()).isEqualTo(1);
        assertThat(row.revenue()).isEqualTo(q.totalPrice());
        assertThat(row.productionCost()).isEqualTo(q.totalCost());
        assertThat(row.profit()).isEqualTo(Math.round((q.totalPrice() - q.totalCost()) * 100.0) / 100.0);
        assertThat(row.hoursSold()).isEqualTo(6.0);
        assertThat(r.totals().revenue()).isEqualTo(q.totalPrice());
    }
}
