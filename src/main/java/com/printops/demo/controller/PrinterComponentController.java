// src/main/java/com/printops/demo/controller/PrinterComponentController.java
package com.printops.demo.controller;

import com.printops.demo.dto.PrinterComponentDTO;
import com.printops.demo.dto.UpdateComponentRequest;
import com.printops.demo.entity.ComponentType;
import com.printops.demo.service.PrinterComponentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Vida útil de los repuestos de desgaste de una impresora (calidad y reemplazos).
@RestController
@RequestMapping("/api/printers/{printerId}/components")
public class PrinterComponentController {

    private final PrinterComponentService componentService;

    public PrinterComponentController(PrinterComponentService componentService) {
        this.componentService = componentService;
    }

    @GetMapping
    public ResponseEntity<List<PrinterComponentDTO>> list(@PathVariable Long printerId) {
        return ResponseEntity.ok(componentService.list(printerId));
    }

    @PutMapping("/{component}")
    public ResponseEntity<PrinterComponentDTO> update(
            @PathVariable Long printerId,
            @PathVariable ComponentType component,
            @Valid @RequestBody UpdateComponentRequest request) {
        return ResponseEntity.ok(componentService.update(printerId, component, request.quality(), request.customLifespanHours()));
    }

    @PostMapping("/{component}/replace")
    public ResponseEntity<PrinterComponentDTO> replace(@PathVariable Long printerId, @PathVariable ComponentType component) {
        return ResponseEntity.ok(componentService.replace(printerId, component));
    }
}
