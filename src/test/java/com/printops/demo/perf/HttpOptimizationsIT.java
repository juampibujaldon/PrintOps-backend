package com.printops.demo.perf;

import com.printops.demo.entity.*;
import com.printops.demo.service.JwtService;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Transporte HTTP real (Tomcat embebido): compresion, cache de /uploads y que los
// endpoints principales sigan funcionando con open-in-view desactivado.
class HttpOptimizationsIT extends IntegrationTestBase {

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired Environment environment;
    @Autowired com.printops.demo.storage.FileStorage fileStorage;

    private final HttpClient http = HttpClient.newHttpClient();
    private Path uploaded;

    @AfterEach
    void cleanUpload() throws IOException {
        if (uploaded != null) Files.deleteIfExists(uploaded);
    }

    private HttpResponse<String> send(String method, String path, User as, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(
                        as.getEmail(), as.getRole().name(), as.getWorkspaceId()))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, User as, String... headers) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + jwtService.generateAccessToken(
                        as.getEmail(), as.getRole().name(), as.getWorkspaceId()));
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return http.send(b.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void openInViewIsDisabled() {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    @Test
    void largeJsonResponsesAreGzipped() throws Exception {
        World w = newWorld();
        addParts(w, 10);
        addOrders(w, 2, 5);

        HttpResponse<String> gz = get("/api/orders", w.manager, "Accept-Encoding", "gzip");

        assertThat(gz.statusCode()).isEqualTo(200);
        assertThat(gz.headers().firstValue("Content-Encoding")).hasValue("gzip");
    }

    @Test
    void storedFilesNeedAValidUnexpiredSignature() throws Exception {
        String key = "orders/" + UUID.randomUUID() + ".png";
        Path dir = Path.of(environment.getProperty("app.storage.local-dir", "uploads"), "orders");
        Files.createDirectories(dir);
        uploaded = dir.resolve(key.substring("orders/".length()));
        Files.write(uploaded, new byte[]{1, 2, 3});

        String url = fileStorage.urlFor(key);
        assertThat(url).startsWith("/api/files/" + key + "?exp=");
        HttpResponse<byte[]> ok = get(url);
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).containsExactly(1, 2, 3);
        assertThat(ok.headers().firstValue("Cache-Control").orElse("")).contains("private");

        // Firma alterada, vencida o clave fuera del storage: 404, sin revelar nada.
        assertThat(get(url.replaceAll("sig=.", "sig=X")).statusCode()).isEqualTo(404);
        String expired = url.replaceAll("exp=\\d+", "exp=1");
        assertThat(get(expired).statusCode()).isEqualTo(404);
        assertThat(get("/api/files/../../build.gradle.kts?exp=99999999999&sig=x").statusCode()).isIn(400, 401, 404);
        // El viejo /uploads público ya no existe.
        assertThat(get("/uploads/" + key).statusCode()).isIn(401, 403, 404);
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofByteArray());
    }

    // Recorre los GET principales con open-in-view apagado: ninguno debe tocar
    // asociaciones lazy fuera de una transaccion (LazyInitializationException => 500).
    @Test
    void mainEndpointsWorkWithoutOpenInView() throws Exception {
        World w = newWorld();
        addParts(w, 10);
        addOrders(w, 2, 3);
        Long orderId = w.orderIds.get(0);
        Long printerId = w.printers.get(0).getId();
        String[] paths = {
                "/api/orders", "/api/orders/" + orderId, "/api/orders/" + orderId + "/history",
                "/api/orders?status=PENDING&printerId=" + printerId,
                "/api/printers", "/api/printers/" + printerId, "/api/printers/due-maintenance",
                "/api/printers/" + printerId + "/history?page=0&size=2",
                "/api/printers/" + printerId + "/history/metrics",
                "/api/printers/" + printerId + "/history/parts",
                "/api/printers/" + printerId + "/components",
                "/api/parts", "/api/parts/low-stock", "/api/parts/categories",
                "/api/parts/" + w.parts.get(0).getId(), "/api/parts/" + w.parts.get(0).getId() + "/movements",
                "/api/quotes", "/api/rules?printerId=" + printerId,
                "/api/notifications", "/api/metrics/dashboard",
        };
        for (String path : paths) {
            HttpResponse<String> res = get(path, w.manager);
            assertThat(res.statusCode()).as("GET " + path + " -> " + res.body()).isEqualTo(200);
        }
    }

    // Escrituras principales de punta a punta (alta de orden, cambio de estado, presupuesto + PDF).
    @Test
    void mainWritesWorkWithoutOpenInView() throws Exception {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P");

        HttpResponse<String> created = send("POST", "/api/orders", w.tech,
                "{\"printerId\":" + printer.getId() + ",\"type\":\"PREVENTIVE\",\"estimatedTimeMinutes\":30,"
                        + "\"checklistItems\":[{\"text\":\"Limpiar\",\"done\":false,\"na\":false}]}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(200);
        Long orderId = Long.valueOf(created.body().replaceAll(".*?\"id\":([0-9]+).*", "$1"));

        HttpResponse<String> started = send("PATCH", "/api/orders/" + orderId + "/status", w.tech,
                "{\"newStatus\":\"IN_PROGRESS\"}");
        assertThat(started.statusCode()).as(started.body()).isEqualTo(200);

        HttpResponse<String> quote = send("POST", "/api/quotes", w.tech,
                "{\"filamentGrams\":100,\"filamentPricePerGram\":0.05,\"filamentType\":\"PLA\",\"printerWatts\":200,"
                        + "\"energyPriceKwh\":0.1,\"printingHours\":5,\"marginPercent\":30,\"printerId\":" + printer.getId() + "}");
        assertThat(quote.statusCode()).as(quote.body()).isEqualTo(200);
        String quoteId = quote.body().replaceAll(".*?\"id\":([0-9]+).*", "$1");
        assertThat(get("/api/quotes/" + quoteId, w.tech).statusCode()).isEqualTo(200);
        assertThat(get("/api/quotes/" + quoteId + "/pdf", w.tech).statusCode()).isEqualTo(200);
    }
}
