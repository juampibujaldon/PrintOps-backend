// src/main/java/com/printops/demo/repository/NotificationPreferenceRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
}
