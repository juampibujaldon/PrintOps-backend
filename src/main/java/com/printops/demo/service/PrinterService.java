// src/main/java/com/printops/demo/service/PrinterService.java
package com.printops.demo.service;

import com.printops.demo.dto.CreatePrinterRequest;
import com.printops.demo.dto.PrinterResponseDTO;
import com.printops.demo.entity.Printer;
import com.printops.demo.entity.PrinterStatus;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PrinterService {

    private static final Logger log = LoggerFactory.getLogger(PrinterService.class);

    private final PrinterRepository printerRepository;
    private final String uploadDir = "uploads/printers/";

    public PrinterService(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!");
        }
    }

    // FIX 3: workspace del usuario autenticado, usado para filtrar todo.
    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    @Transactional
    public PrinterResponseDTO createPrinter(CreatePrinterRequest dto, MultipartFile photo) {
        log.info("Creando impresora: serialNumber={}, brand={}, model={}",
                dto.serialNumber(), dto.brand(), dto.model());

        if (printerRepository.findBySerialNumberAndWorkspaceId(dto.serialNumber(), wsId()).isPresent()) {
            throw new IllegalArgumentException("El número de serie ya está registrado.");
        }

        Printer printer = new Printer();
        printer.setBrand(dto.brand());
        printer.setModel(dto.model());
        printer.setSerialNumber(dto.serialNumber());
        printer.setPurchaseDate(dto.purchaseDate());
        printer.setStatus(PrinterStatus.valueOf(dto.status()));
        printer.setName(blankToNull(dto.name()));
        printer.setLocation(blankToNull(dto.location()));
        printer.setNextMaintenanceDate(dto.nextMaintenanceDate());
        printer.setWorkspaceId(wsId());

        printer.setQrCodeData("printops://printer/" + dto.serialNumber());

        if (photo != null && !photo.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename();
            try {
                Path targetLocation = Paths.get(uploadDir).resolve(fileName);
                Files.copy(photo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/printers/")
                        .path(fileName)
                        .toUriString();
                printer.setPhotoUrl(fileDownloadUri);
            } catch (IOException ex) {
                log.error("Error guardando la foto", ex);
                throw new RuntimeException("No se pudo guardar la foto.", ex);
            }
        }

        Printer saved = printerRepository.save(printer);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PrinterResponseDTO> getAllPrinters(String location) {
        List<Printer> printers = (location != null && !location.isBlank())
                ? printerRepository.findByWorkspaceIdAndLocationContainingIgnoreCase(wsId(), location)
                : printerRepository.findByWorkspaceId(wsId());
        return printers.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PrinterResponseDTO getBySerialNumber(String serialNumber) {
        return printerRepository.findBySerialNumberAndWorkspaceId(serialNumber, wsId())
                .map(this::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con serie " + serialNumber));
    }

    @Transactional
    public PrinterResponseDTO updateNextMaintenanceDate(Long id, LocalDate nextMaintenanceDate) {
        Printer printer = printerRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + id));
        printer.setNextMaintenanceDate(nextMaintenanceDate);
        return toResponse(printerRepository.save(printer));
    }

    @Transactional(readOnly = true)
    public List<PrinterResponseDTO> getPrintersWithDueMaintenance(LocalDate date) {
        return printerRepository.findByWorkspaceIdAndNextMaintenanceDateBefore(wsId(), date).stream()
                .map(this::toResponse)
                .toList();
    }

    private PrinterResponseDTO toResponse(Printer p) {
        return new PrinterResponseDTO(
                p.getId(),
                p.getName(),
                p.getBrand(),
                p.getModel(),
                p.getSerialNumber(),
                p.getPurchaseDate(),
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getLocation(),
                p.getNextMaintenanceDate(),
                p.getPhotoUrl(),
                p.getQrCodeData()
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
