// src/main/java/com/printops/demo/controller/PrinterController.java
package com.printops.demo.controller;

import com.printops.demo.entity.Printer;
import com.printops.demo.service.PrinterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/printers")
public class PrinterController {

    private final PrinterService printerService;

    public PrinterController(PrinterService printerService) {
        this.printerService = printerService;
    }

    @PostMapping
    public ResponseEntity<Printer> createPrinter(
            @RequestPart("printer") Printer printer,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        
        Printer saved = printerService.createPrinter(printer, photo);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<java.util.List<Printer>> getAllPrinters() {
        return ResponseEntity.ok(printerService.getAllPrinters());
    }
}
