package com.printops.demo.support;

import com.printops.demo.entity.*;
import com.printops.demo.repository.*;
import com.printops.demo.security.AuthUser;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

// Base de todos los tests de integracion: Postgres descartable en Docker (nunca la
// base de desarrollo), Hibernate Statistics y un generador de datos de prueba.
// Un unico contenedor por JVM de tests (se reutiliza el contexto de Spring).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestFlywayConfig.class)
public abstract class IntegrationTestBase {

    // Sin Docker se puede usar una base local descartable: TEST_JDBC_URL (+ TEST_DB_USER /
    // TEST_DB_PASSWORD). El nombre de la base tiene que terminar en "_test": cada corrida la
    // limpia entera (TestFlywayConfig) antes de migrar.
    private static final String EXTERNAL_URL = System.getenv("TEST_JDBC_URL");
    static final PostgreSQLContainer POSTGRES = EXTERNAL_URL == null ? new PostgreSQLContainer("postgres:16-alpine") : null;
    private static final AtomicLong SEQ = new AtomicLong(1000);
    private static final Path MEASUREMENTS = Path.of("build", "perf-measurements.txt");

    static {
        if (POSTGRES != null) {
            POSTGRES.start();
        }
    }

    static String jdbcUrl() {
        return POSTGRES != null ? POSTGRES.getJdbcUrl() : EXTERNAL_URL;
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "printops"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        }
    }

    // Red de seguridad: jamas correr contra la base de desarrollo (localhost:5432/printops).
    // Con una base externa solo se acepta una cuyo nombre termine en "_test".
    @BeforeAll
    static void neverTouchDevDatabase() {
        if (POSTGRES != null) {
            assertThat(POSTGRES.getJdbcUrl()).doesNotContain(":5432/").doesNotContain("/printops");
        } else {
            String db = EXTERNAL_URL.replaceFirst("\\?.*$", "");
            db = db.substring(db.lastIndexOf('/') + 1);
            assertThat(db).as("TEST_JDBC_URL debe apuntar a una base *_test").endsWith("_test");
        }
    }

    @Autowired protected EntityManagerFactory emf;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PlatformTransactionManager txManager;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PrinterRepository printerRepository;
    @Autowired protected MaintenanceOrderRepository orderRepository;
    @Autowired protected MaintenanceRuleRepository ruleRepository;
    @Autowired protected SparePartRepository sparePartRepository;
    @Autowired protected StatusHistoryRepository historyRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Medicion ─────────────────────────────────────────────────────────────
    protected Statistics stats() {
        return emf.unwrap(SessionFactory.class).getStatistics();
    }

    public record Measurement(long statements, long entitiesLoaded, long collectionsLoaded) {}

    // Ejecuta la accion con las estadisticas en cero y registra los numeros.
    protected <T> Measured<T> measure(String label, Supplier<T> action) {
        Statistics s = stats();
        s.clear();
        T result = action.get();
        Measurement m = new Measurement(s.getPrepareStatementCount(), s.getEntityLoadCount(), s.getCollectionLoadCount());
        record(label, m);
        return new Measured<>(result, m);
    }

    public record Measured<T>(T result, Measurement m) {}

    private static synchronized void record(String label, Measurement m) {
        String line = "PERF | " + label + " | statements=" + m.statements()
                + " | entitiesLoaded=" + m.entitiesLoaded() + " | collectionsLoaded=" + m.collectionsLoaded();
        System.out.println(line);
        try {
            Files.createDirectories(MEASUREMENTS.getParent());
            Files.writeString(MEASUREMENTS, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // La salida estandar ya lo registro.
        }
    }

    protected long entityLoads(Class<?> type) {
        return stats().getEntityStatistics(type.getName()).getLoadCount();
    }

    // ── Seguridad / tenant ───────────────────────────────────────────────────
    protected void loginAs(User user) {
        AuthUser principal = new AuthUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    protected <T> T inTx(Supplier<T> work) {
        return new TransactionTemplate(txManager).execute(s -> work.get());
    }

    // ── Datos de prueba ──────────────────────────────────────────────────────
    public static final class World {
        public Long workspaceId;
        public User manager;
        public User tech;
        public User tech2;
        public List<Printer> printers = new ArrayList<>();
        public List<SparePart> parts = new ArrayList<>();
        public List<Long> orderIds = new ArrayList<>();
        public List<MaintenanceRule> rules = new ArrayList<>();
    }

    protected User newUser(String prefix, Role role, Long workspaceId) {
        User u = new User();
        u.setEmail(prefix + "-" + SEQ.incrementAndGet() + "@test.local");
        u.setPassword("x");
        u.setRole(role);
        u.setWorkspaceId(workspaceId);
        u.setEmailVerified(true);
        return userRepository.save(u);
    }

    protected Printer newPrinter(Long workspaceId, String name) {
        Printer p = new Printer();
        p.setBrand("Prusa");
        p.setModel("MK4");
        p.setName(name);
        p.setSerialNumber("SN-" + SEQ.incrementAndGet());
        p.setPurchaseDate(java.time.LocalDate.of(2024, 1, 1));
        p.setStatus(PrinterStatus.OPERATIVE);
        p.setWorkspaceId(workspaceId);
        return printerRepository.save(p);
    }

    protected World newWorld() {
        World w = new World();
        w.workspaceId = SEQ.incrementAndGet() * 1000; // no choca con el DataSeeder (workspace 1)
        w.manager = newUser("manager", Role.MANAGER, w.workspaceId);
        w.tech = newUser("tech", Role.TECNICO, w.workspaceId);
        w.tech2 = newUser("tech", Role.TECNICO, w.workspaceId);
        return w;
    }

    // Repuestos del catalogo: 4 categorias, cada 4to esta en stock bajo.
    protected void addParts(World w, int count) {
        String[] categories = {"Extrusion", "Electronica", "Movimiento", "Cama"};
        for (int i = 0; i < count; i++) {
            SparePart sp = new SparePart();
            sp.setName((i % 5 == 0 ? "Boquilla " : "Pieza ") + i);
            sp.setPartNumber("PN-" + w.workspaceId + "-" + i);
            sp.setCategory(categories[i % 4]);
            boolean low = i % 4 == 0;
            sp.setStock(low ? 1 : 20);
            sp.setMinStock(low ? 2 : 3);
            sp.setUnitPrice(5.0 + i);
            sp.setWorkspaceId(w.workspaceId);
            w.parts.add(sparePartRepository.save(sp));
        }
    }

    // printers x ordersPerPrinter ordenes; cada una con 3 checklist, 3 piezas
    // (2 de catalogo + 1 externa), 2 fotos y 1 fila de historial.
    // createdAt determinista (cada orden, una hora mas vieja que la anterior).
    protected void addOrders(World w, int printerCount, int ordersPerPrinter) {
        OrderStatus[] statuses = {OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.IN_REVIEW, OrderStatus.COMPLETED};
        OrderType[] types = {OrderType.PREVENTIVE, OrderType.CORRECTIVE, OrderType.CALIBRATION};
        for (int p = 0; p < printerCount; p++) {
            Printer printer = newPrinter(w.workspaceId, "Printer " + p);
            w.printers.add(printer);
            for (int i = 0; i < ordersPerPrinter; i++) {
                int n = p * ordersPerPrinter + i;
                MaintenanceOrder o = new MaintenanceOrder();
                o.setPrinter(printer);
                o.setAssignedTo(n % 2 == 0 ? w.tech : w.tech2);
                o.setType(types[n % 3]);
                o.setStatus(statuses[n % 4]);
                o.setWorkspaceId(w.workspaceId);
                o.setDescription("Orden " + n);
                o.setEstimatedTimeMinutes(60);
                if (o.getStatus() == OrderStatus.COMPLETED) o.setActualTimeMinutes(30 + n);
                for (int c = 0; c < 3; c++) {
                    OrderChecklistItem ci = new OrderChecklistItem();
                    ci.setText("Paso " + c);
                    o.addChecklistItem(ci);
                }
                for (int k = 0; k < 2 && !w.parts.isEmpty(); k++) {
                    OrderPart op = new OrderPart();
                    op.setQuantity(1);
                    op.setSparePart(w.parts.get((n + k) % w.parts.size()));
                    o.addPart(op);
                }
                OrderPart external = new OrderPart();
                external.setQuantity(2);
                external.setExternalPartName("Externa " + n);
                external.setExternalUnitPrice(3.5);
                o.addPart(external);
                for (int k = 0; k < 2; k++) {
                    OrderPhoto ph = new OrderPhoto();
                    ph.setStorageKey("orders/" + java.util.UUID.randomUUID() + ".jpg");
                    o.addPhoto(ph);
                }
                MaintenanceOrder saved = orderRepository.save(o);
                w.orderIds.add(saved.getId());

                StatusHistory h = new StatusHistory();
                h.setOrderId(saved.getId());
                h.setToStatus(OrderStatus.PENDING);
                h.setChangedBy(w.tech);
                historyRepository.save(h);

                // createdAt determinista; updatedAt = createdAt + (n%5+1)*90min + 30s (para promedios)
                jdbc.update("UPDATE maintenance_orders SET created_at = now() - make_interval(hours => ?),"
                                + " updated_at = now() - make_interval(hours => ?) + make_interval(mins => ?, secs => 30) WHERE id = ?",
                        n + 1, n + 1, (n % 5 + 1) * 90, saved.getId());
            }
        }
    }

    // Reglas TIME_BASED ya vencidas (disparan al evaluarse), repartidas entre las impresoras.
    protected void addTriggeredRules(World w, int count) {
        for (int i = 0; i < count; i++) {
            Printer printer = w.printers.get(i % w.printers.size());
            MaintenanceRule r = new MaintenanceRule();
            r.setPrinter(printer);
            r.setTriggerType(TriggerType.TIME_BASED);
            r.setTriggerValue(30);
            r.setMaintenanceType(OrderType.PREVENTIVE);
            r.setChecklistTemplate("[\"Limpiar\",\"Lubricar\"]");
            r.setActive(true);
            r.setWorkspaceId(w.workspaceId);
            r.setCreatedBy(w.manager);
            r.setLastTriggeredAt(java.time.LocalDate.now().minusDays(60));
            w.rules.add(ruleRepository.save(r));
        }
    }

    // Aisla las reglas: apaga las de otros tests para que evaluateAllRules vea solo las propias.
    protected void deactivateAllRules() {
        jdbc.update("UPDATE maintenance_rules SET active = false");
    }

    protected static Instant daysAgo(long days) {
        return Instant.now().minus(java.time.Duration.ofDays(days));
    }
}
