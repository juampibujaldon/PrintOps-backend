// src/main/java/com/printops/demo/controller/NotificationController.java
package com.printops.demo.controller;

import com.printops.demo.dto.NotificationResponse;
import com.printops.demo.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // Devuelve las notificaciones del usuario autenticado.
    // Con `page` responde una página (PageResponse); sin `page`, la lista completa.
    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (page != null) {
            return ResponseEntity.ok(notificationService.pageForEmail(userDetails.getUsername(), page, size));
        }
        return ResponseEntity.ok(notificationService.listForEmail(userDetails.getUsername()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userDetails.getUsername())));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(notificationService.markRead(id, userDetails.getUsername()));
    }
}
