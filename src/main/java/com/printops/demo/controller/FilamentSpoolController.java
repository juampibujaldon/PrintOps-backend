package com.printops.demo.controller;

import com.printops.demo.dto.FilamentSpoolDTO;
import com.printops.demo.dto.SpoolRequest;
import com.printops.demo.service.FilamentSpoolService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/spools")
public class FilamentSpoolController {

    private final FilamentSpoolService spoolService;

    public FilamentSpoolController(FilamentSpoolService spoolService) {
        this.spoolService = spoolService;
    }

    @GetMapping
    public ResponseEntity<List<FilamentSpoolDTO>> list(@RequestParam(defaultValue = "false") boolean archived) {
        return ResponseEntity.ok(spoolService.list(archived));
    }

    @PostMapping
    public ResponseEntity<FilamentSpoolDTO> create(@Valid @RequestBody SpoolRequest request) {
        return ResponseEntity.ok(spoolService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FilamentSpoolDTO> update(@PathVariable Long id, @Valid @RequestBody SpoolRequest request) {
        return ResponseEntity.ok(spoolService.update(id, request));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<FilamentSpoolDTO> archive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        return ResponseEntity.ok(spoolService.archive(id, Boolean.TRUE.equals(body.get("archived"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        spoolService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
