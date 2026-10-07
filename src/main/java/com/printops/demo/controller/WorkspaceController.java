// src/main/java/com/printops/demo/controller/WorkspaceController.java
package com.printops.demo.controller;

import com.printops.demo.dto.InviteRequest;
import com.printops.demo.dto.QuoteDefaultsDTO;
import com.printops.demo.dto.UpdateWorkspaceRequest;
import com.printops.demo.dto.WorkspaceResponse;
import com.printops.demo.service.WorkspaceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/me")
    public ResponseEntity<WorkspaceResponse> getMine(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(workspaceService.getMine(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<WorkspaceResponse> updateMine(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ResponseEntity.ok(workspaceService.updateMine(userDetails.getUsername(), request));
    }

    // ── Quote Defaults ──

    @GetMapping("/quote-defaults")
    public ResponseEntity<QuoteDefaultsDTO> getQuoteDefaults(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(workspaceService.getQuoteDefaults(userDetails.getUsername()));
    }

    @PutMapping("/quote-defaults")
    public ResponseEntity<QuoteDefaultsDTO> updateQuoteDefaults(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuoteDefaultsDTO request) {
        return ResponseEntity.ok(workspaceService.updateQuoteDefaults(userDetails.getUsername(), request));
    }

    @GetMapping("/canvas")
    public ResponseEntity<String> getCanvasLayout(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(workspaceService.getCanvasLayout(userDetails.getUsername()));
    }

    @PutMapping("/canvas")
    public ResponseEntity<String> updateCanvasLayout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody String layoutJson) {
        return ResponseEntity.ok(workspaceService.updateCanvasLayout(userDetails.getUsername(), layoutJson));
    }

    // ── Invites ──

    @PostMapping("/invites")
    public ResponseEntity<Map<String, String>> invite(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody InviteRequest request) {
        String token = workspaceService.invite(userDetails.getUsername(), request.email());
        return ResponseEntity.ok(Map.of(
                "message", "Invitación enviada a " + request.email(),
                "inviteToken", token
        ));
    }
}
