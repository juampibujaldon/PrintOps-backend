// src/main/java/com/printops/demo/service/PrinterService.java
package com.printops.demo.service;

import com.printops.demo.entity.Printer;
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

    @Transactional
    public Printer createPrinter(Printer printer, MultipartFile photo) {
        if (printerRepository.findBySerialNumber(printer.getSerialNumber()).isPresent()) {
            throw new IllegalArgumentException("El número de serie ya está registrado.");
        }

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

        return printerRepository.save(printer);
    }

    @Transactional(readOnly = true)
    public java.util.List<Printer> getAllPrinters() {
        return printerRepository.findAll();
    }
}
