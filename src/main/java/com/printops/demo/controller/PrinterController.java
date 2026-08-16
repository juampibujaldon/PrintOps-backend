// src/main/java/com/printops/demo/controller/PrinterController.java
package com.printops.demo.controller;

import com.printops.demo.dto.CreatePrinterRequest;
import com.printops.demo.dto.NextMaintenanceDateRequest;
import com.printops.demo.dto.PrinterResponseDTO;
import com.printops.demo.service.PrinterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/printers")
public class PrinterController {

    private static final Logger log = LoggerFactory.getLogger(PrinterController.class);

    private final PrinterService printerService;

    public PrinterController(PrinterService printerService) {
        this.printerService = printerService;
    }

    // ERR-03: consumes explícito multipart/form-data. El part "printer" es JSON
    // (CreatePrinterRequest) y el part "photo" es opcional.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PrinterResponseDTO> createPrinter(
            @Valid @RequestPart("printer") CreatePrinterRequest printer,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {

        log.info("POST /api/printers: serialNumber={}", printer.serialNumber());
        PrinterResponseDTO saved = printerService.createPrinter(printer, photo);
        return ResponseEntity.ok(saved);
    }

    // FIX 3: query param opcional ?location= para filtrar.
    @GetMapping
    public ResponseEntity<List<PrinterResponseDTO>> getAllPrinters(
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(printerService.getAllPrinters(location));
    }

    // Lookup por número de serie, usado por el lector de QR.
    @GetMapping("/by-serial/{serialNumber}")
    public ResponseEntity<PrinterResponseDTO> getBySerialNumber(@PathVariable String serialNumber) {
        return ResponseEntity.ok(printerService.getBySerialNumber(serialNumber));
    }

    // FIX 4: actualizar fecha de próximo mantenimiento al cerrar una orden.
    @PatchMapping("/{id}/next-maintenance-date")
    public ResponseEntity<PrinterResponseDTO> updateNextMaintenanceDate(
            @PathVariable Long id,
            @Valid @RequestBody NextMaintenanceDateRequest request) {
        return ResponseEntity.ok(printerService.updateNextMaintenanceDate(id, request.nextMaintenanceDate()));
    }

    // FIX 4: listado de impresoras con mantenimiento vencido.
    @GetMapping("/due-maintenance")
    public ResponseEntity<List<PrinterResponseDTO>> getDueMaintenance() {
        return ResponseEntity.ok(printerService.getPrintersWithDueMaintenance(LocalDate.now()));
    }
}
