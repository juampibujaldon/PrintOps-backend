// src/main/java/com/printops/demo/repository/LoginAuditRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Long> {
}
