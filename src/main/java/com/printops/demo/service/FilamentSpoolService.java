package com.printops.demo.service;

import com.printops.demo.dto.FilamentSpoolDTO;
import com.printops.demo.dto.SpoolRequest;
import com.printops.demo.entity.FilamentSpool;
import com.printops.demo.entity.Printer;
import com.printops.demo.entity.Role;
import com.printops.demo.entity.User;
import com.printops.demo.repository.FilamentSpoolRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Inventario de filamento por bobina.
@Service
public class FilamentSpoolService {

    // Aviso de bobina por terminarse: menos de 100 g o del 10%.
    static final double LOW_GRAMS = 100.0;
    static final double LOW_PERCENT = 10.0;

    private final FilamentSpoolRepository spoolRepository;
    private final PrinterRepository printerRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LiveUpdates liveUpdates;

    public FilamentSpoolService(FilamentSpoolRepository spoolRepository, PrinterRepository printerRepository,
                                UserRepository userRepository, NotificationService notificationService,
                                LiveUpdates liveUpdates) {
        this.spoolRepository = spoolRepository;
        this.printerRepository = printerRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.liveUpdates = liveUpdates;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    @Transactional(readOnly = true)
    public List<FilamentSpoolDTO> list(boolean archived) {
        return spoolRepository.findByWorkspaceIdAndArchivedOrderByMaterialAscColorAsc(wsId(), archived)
                .stream().map(FilamentSpoolService::toDTO).toList();
    }

    @Transactional
    public FilamentSpoolDTO create(SpoolRequest r) {
        FilamentSpool s = new FilamentSpool();
        s.setWorkspaceId(wsId());
        apply(s, r);
        if (r.remainingGrams() == null) s.setRemainingGrams(r.initialGrams());
        return toDTO(spoolRepository.save(s));
    }

    @Transactional
    public FilamentSpoolDTO update(Long id, SpoolRequest r) {
        FilamentSpool s = find(id);
        apply(s, r);
        if (!isLow(s)) s.setLowAlertSent(false);
        return toDTO(spoolRepository.save(s));
    }

    @Transactional
    public FilamentSpoolDTO archive(Long id, boolean archived) {
        FilamentSpool s = find(id);
        s.setArchived(archived);
        if (archived) s.setPrinter(null);
        return toDTO(spoolRepository.save(s));
    }

    @Transactional
    public void delete(Long id) {
        spoolRepository.delete(find(id));
    }

    // Descuenta lo consumido por un trabajo y avisa una vez cuando queda poco.
    @Transactional
    public void consume(FilamentSpool spool, double grams) {
        if (spool == null || grams <= 0) return;
        spool.setRemainingGrams(Math.max(0.0, spool.getRemainingGrams() - grams));
        if (isLow(spool) && !spool.isLowAlertSent()) {
            spool.setLowAlertSent(true);
            String msg = "Queda poco filamento en la bobina " + label(spool) + ": "
                    + Math.round(spool.getRemainingGrams()) + " g.";
            for (User m : userRepository.findByRoleAndWorkspaceId(Role.MANAGER, spool.getWorkspaceId())) {
                notificationService.createFor(m.getId(), "LOW_FILAMENT", msg, null);
                liveUpdates.toUser(m.getId(), "notification", Map.of("title", "Filamento bajo", "body", msg));
            }
        }
        spoolRepository.save(spool);
    }

    private FilamentSpool find(Long id) {
        return spoolRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Bobina no encontrada"));
    }

    private void apply(FilamentSpool s, SpoolRequest r) {
        s.setMaterial(r.material());
        s.setColor(blankToNull(r.color()));
        s.setBrand(blankToNull(r.brand()));
        s.setInitialGrams(r.initialGrams());
        if (r.remainingGrams() != null) {
            if (r.remainingGrams() > r.initialGrams()) {
                throw new IllegalArgumentException("Los gramos restantes no pueden superar los iniciales.");
            }
            s.setRemainingGrams(r.remainingGrams());
        }
        s.setCostPerKg(r.costPerKg());
        Printer printer = r.printerId() != null
                ? printerRepository.findByIdAndWorkspaceId(r.printerId(), wsId())
                        .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada"))
                : null;
        s.setPrinter(printer);
    }

    static boolean isLow(FilamentSpool s) {
        double remaining = s.getRemainingGrams() != null ? s.getRemainingGrams() : 0;
        double initial = s.getInitialGrams() != null && s.getInitialGrams() > 0 ? s.getInitialGrams() : 1;
        return remaining < LOW_GRAMS || remaining / initial * 100.0 < LOW_PERCENT;
    }

    public static String label(FilamentSpool s) {
        StringBuilder b = new StringBuilder(s.getMaterial().name());
        if (s.getColor() != null) b.append(' ').append(s.getColor());
        if (s.getBrand() != null) b.append(" (").append(s.getBrand()).append(')');
        return b.toString();
    }

    static FilamentSpoolDTO toDTO(FilamentSpool s) {
        double initial = s.getInitialGrams() != null ? s.getInitialGrams() : 0;
        double remaining = s.getRemainingGrams() != null ? s.getRemainingGrams() : 0;
        Printer p = s.getPrinter();
        return new FilamentSpoolDTO(s.getId(), s.getMaterial().name(), s.getColor(), s.getBrand(), initial, remaining,
                s.getCostPerKg(), Math.round(s.pricePerGram() * 10000.0) / 10000.0,
                initial > 0 ? Math.round(remaining / initial * 1000.0) / 10.0 : 0, isLow(s),
                p != null ? p.getId() : null, p != null ? (p.getName() != null ? p.getName() : p.getModel()) : null,
                s.isArchived(), s.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
