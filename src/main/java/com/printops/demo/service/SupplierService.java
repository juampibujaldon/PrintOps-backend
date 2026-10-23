package com.printops.demo.service;

import com.printops.demo.dto.SparePartDTO;
import com.printops.demo.dto.SupplierDTO;
import com.printops.demo.entity.Supplier;
import com.printops.demo.repository.SupplierRepository;
import com.printops.demo.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SparePartService sparePartService;

    public SupplierService(SupplierRepository supplierRepository, SparePartService sparePartService) {
        this.supplierRepository = supplierRepository;
        this.sparePartService = sparePartService;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    @Transactional(readOnly = true)
    public List<SupplierDTO> list() {
        return supplierRepository.findByWorkspaceIdOrderByNameAsc(wsId()).stream().map(SupplierService::toDTO).toList();
    }

    @Transactional
    public SupplierDTO create(SupplierDTO r) {
        Supplier s = new Supplier();
        s.setWorkspaceId(wsId());
        apply(s, r);
        return toDTO(supplierRepository.save(s));
    }

    @Transactional
    public SupplierDTO update(Long id, SupplierDTO r) {
        Supplier s = find(id);
        apply(s, r);
        return toDTO(supplierRepository.save(s));
    }

    @Transactional
    public void delete(Long id) {
        supplierRepository.delete(find(id));
    }

    @Transactional
    public SparePartDTO assignToPart(Long partId, Long supplierId) {
        return sparePartService.assignSupplier(partId, supplierId != null ? find(supplierId) : null);
    }

    Supplier find(Long id) {
        return supplierRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Proveedor no encontrado"));
    }

    private static void apply(Supplier s, SupplierDTO r) {
        s.setName(r.name().trim());
        s.setEmail(blank(r.email()));
        s.setPhone(blank(r.phone()));
        s.setWebsite(blank(r.website()));
        s.setNotes(blank(r.notes()));
    }

    static SupplierDTO toDTO(Supplier s) {
        return new SupplierDTO(s.getId(), s.getName(), s.getEmail(), s.getPhone(), s.getWebsite(), s.getNotes());
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
