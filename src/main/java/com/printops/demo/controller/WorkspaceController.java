// src/main/java/com/printops/demo/controller/WorkspaceController.java
package com.printops.demo.controller;

import com.printops.demo.dto.InviteRequest;
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
