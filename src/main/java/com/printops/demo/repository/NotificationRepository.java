// src/main/java/com/printops/demo/repository/NotificationRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Solo las notificaciones dirigidas al usuario. Las "globales" (recipient null)
    // no se exponen porque no tienen workspace y se filtrarían entre talleres.
    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);
}
