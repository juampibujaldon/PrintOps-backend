package com.printops.demo.controller;

import com.printops.demo.dto.FinishPrintJobRequest;
import com.printops.demo.dto.PrintJobDTO;
import com.printops.demo.dto.PrintJobRequest;
import com.printops.demo.service.PrintJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class PrintJobController {

    private final PrintJobService jobService;

    public PrintJobController(PrintJobService jobService) {
        this.jobService = jobService;
    }

    // active=true (default): cola actual; false: historial.
    @GetMapping
    public ResponseEntity<List<PrintJobDTO>> list(@RequestParam(defaultValue = "true") boolean active,
                                                  @RequestParam(required = false) Long printerId) {
        return ResponseEntity.ok(jobService.list(active, printerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PrintJobDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.get(id));
    }

    @PostMapping
    public ResponseEntity<PrintJobDTO> create(@Valid @RequestBody PrintJobRequest request,
                                              @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(jobService.create(request, user.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PrintJobDTO> update(@PathVariable Long id, @Valid @RequestBody PrintJobRequest request) {
        return ResponseEntity.ok(jobService.update(id, request));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<PrintJobDTO> start(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.start(id));
    }

    @PostMapping("/{id}/finish")
    public ResponseEntity<PrintJobDTO> finish(@PathVariable Long id,
                                              @Valid @RequestBody(required = false) FinishPrintJobRequest request,
                                              @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(jobService.finish(id, request, user.getUsername()));
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<PrintJobDTO> fail(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(jobService.fail(id, body != null ? body.get("notes") : null));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PrintJobDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.cancel(id));
    }

    @PostMapping("/{id}/requeue")
    public ResponseEntity<PrintJobDTO> requeue(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.requeue(id));
    }
}
