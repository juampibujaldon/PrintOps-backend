// src/main/java/com/printops/demo/repository/WorkspaceInviteRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.WorkspaceInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceInviteRepository extends JpaRepository<WorkspaceInvite, Long> {
    Optional<WorkspaceInvite> findByToken(String token);
}
