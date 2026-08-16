// src/main/java/com/printops/demo/service/PrinterService.java
package com.printops.demo.service;

import com.printops.demo.dto.CreatePrinterRequest;
import com.printops.demo.dto.PrinterResponseDTO;
import com.printops.demo.entity.Printer;
import com.printops.demo.entity.PrinterStatus;
import com.printops.demo.repository.PrinterRepository;
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

    private final PrinterRepository printerRepository;
    private final String uploadDir = "uploads/printers/";

    public PrinterService(PrinterRepository printerRepository) {
        this.printerRepository = printerRepository;
        // Ensure upload directory exists
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!");
        }
    }

    // FIX 2: ahora recibe el DTO (no la entidad) y mapea manualmente.
    // El id lo asigna la DB y el qrCodeData se genera acá, nunca desde el cliente.
    @Transactional
    public PrinterResponseDTO createPrinter(CreatePrinterRequest dto, MultipartFile photo) {
        if (printerRepository.findBySerialNumber(dto.serialNumber()).isPresent()) {
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

        // El dato del QR se genera en el servidor, nunca se acepta del request.
        printer.setQrCodeData(UUID.randomUUID().toString());

        if (photo != null && !photo.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename();
            try {
                Path targetLocation = Paths.get(uploadDir).resolve(fileName);
                Files.copy(photo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

                // Generar URL pública para acceder a la foto
                String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/printers/")
                        .path(fileName)
                        .toUriString();

                printer.setPhotoUrl(fileDownloadUri);
            } catch (IOException ex) {
                throw new RuntimeException("No se pudo guardar la foto.", ex);
            }
        }

        return toResponse(printerRepository.save(printer));
    }

    // FIX 3: filtro opcional por ubicación.
    @Transactional(readOnly = true)
    public List<PrinterResponseDTO> getAllPrinters(String location) {
        List<Printer> printers;
        if (location != null && !location.isBlank()) {
            printers = printerRepository.findByLocationContainingIgnoreCase(location);
        } else {
            printers = printerRepository.findAll();
        }
        return printers.stream().map(this::toResponse).toList();
    }

    // FIX 4: actualiza la fecha del próximo mantenimiento (ej. al cerrar una orden).
    @Transactional
    public PrinterResponseDTO updateNextMaintenanceDate(Long id, LocalDate nextMaintenanceDate) {
        Printer printer = printerRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + id));
        printer.setNextMaintenanceDate(nextMaintenanceDate);
        return toResponse(printerRepository.save(printer));
    }

    // FIX 4: impresoras con mantenimiento vencido (fecha anterior a la indicada).
    @Transactional(readOnly = true)
    public List<PrinterResponseDTO> getPrintersWithDueMaintenance(LocalDate date) {
        return printerRepository.findByNextMaintenanceDateBefore(date).stream()
                .map(this::toResponse)
                .toList();
    }

    // Mapeo entidad -> DTO de respuesta. Nunca se expone la entidad directamente.
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

    // Convierte strings vacíos/espacios en null para no persistir valores vacíos.
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
