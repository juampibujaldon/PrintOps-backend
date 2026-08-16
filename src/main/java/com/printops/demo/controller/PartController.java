// src/main/java/com/printops/demo/controller/PartController.java
package com.printops.demo.controller;

import com.printops.demo.dto.PartRequest;
import com.printops.demo.dto.PartResponse;
import com.printops.demo.service.PartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parts")
public class PartController {

    private final PartService partService;

    public PartController(PartService partService) {
        this.partService = partService;
    }

    @GetMapping
    public ResponseEntity<List<PartResponse>> list(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(partService.list(query));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(partService.get(id));
    }

    @PostMapping
    public ResponseEntity<PartResponse> create(@Valid @RequestBody PartRequest request) {
        return ResponseEntity.ok(partService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PartResponse> update(@PathVariable Long id, @Valid @RequestBody PartRequest request) {
        return ResponseEntity.ok(partService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        partService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Pieza eliminada"));
    }
}
