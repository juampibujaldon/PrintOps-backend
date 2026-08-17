// src/main/java/com/printops/demo/repository/WorkspaceRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
