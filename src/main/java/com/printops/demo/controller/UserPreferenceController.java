// src/main/java/com/printops/demo/controller/UserPreferenceController.java
package com.printops.demo.controller;

import com.printops.demo.dto.NotificationPreferenceDTO;
import com.printops.demo.service.NotificationPreferenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class UserPreferenceController {

    private final NotificationPreferenceService notificationPreferenceService;

    public UserPreferenceController(NotificationPreferenceService notificationPreferenceService) {
        this.notificationPreferenceService = notificationPreferenceService;
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<NotificationPreferenceDTO> getNotificationPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationPreferenceService.get(userDetails.getUsername()));
    }

    @PutMapping("/notification-preferences")
    public ResponseEntity<NotificationPreferenceDTO> updateNotificationPreferences(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody NotificationPreferenceDTO request) {
        return ResponseEntity.ok(notificationPreferenceService.update(userDetails.getUsername(), request));
    }
}
