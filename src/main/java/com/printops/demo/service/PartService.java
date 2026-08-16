// src/main/java/com/printops/demo/service/PartService.java
package com.printops.demo.service;

import com.printops.demo.dto.PartRequest;
import com.printops.demo.dto.PartResponse;
import com.printops.demo.entity.Part;
import com.printops.demo.repository.PartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PartService {

    private final PartRepository partRepository;

    public PartService(PartRepository partRepository) {
        this.partRepository = partRepository;
    }

    @Transactional(readOnly = true)
    public List<PartResponse> list(String query) {
        List<Part> parts = (query == null || query.isBlank())
                ? partRepository.findAll()
                : partRepository.findByNameContainingIgnoreCase(query);
        // Ordenar por nombre para el selector.
        return parts.stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartResponse get(Long id) {
        return toResponse(partRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pieza no encontrada con id " + id)));
    }

    @Transactional
    public PartResponse create(PartRequest request) {
        if (partRepository.existsByPartNumber(request.partNumber())) {
            throw new IllegalArgumentException("Ya existe una pieza con ese número de parte.");
        }
        Part part = new Part();
        part.setName(request.name());
        part.setPartNumber(request.partNumber());
        part.setStockQuantity(request.stockQuantity());
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public PartResponse update(Long id, PartRequest request) {
        Part part = partRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pieza no encontrada con id " + id));

        // Si cambia el número de parte, validar que no colisione.
        if (!part.getPartNumber().equals(request.partNumber())
                && partRepository.existsByPartNumber(request.partNumber())) {
            throw new IllegalArgumentException("Ya existe una pieza con ese número de parte.");
        }

        part.setName(request.name());
        part.setPartNumber(request.partNumber());
        part.setStockQuantity(request.stockQuantity());
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public void delete(Long id) {
        if (!partRepository.existsById(id)) {
            throw new NoSuchElementException("Pieza no encontrada con id " + id);
        }
        partRepository.deleteById(id);
    }

    private PartResponse toResponse(Part p) {
        return new PartResponse(p.getId(), p.getName(), p.getPartNumber(), p.getStockQuantity());
    }
}
