// src/main/java/com/printops/demo/repository/SparePartRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.SparePart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SparePartRepository extends JpaRepository<SparePart, Long> {

    // FIX 3: todas las consultas filtradas por workspace.
    List<SparePart> findByWorkspaceIdOrderByNameAsc(Long workspaceId);

    Optional<SparePart> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<SparePart> findByPartNumber(String partNumber);

    boolean existsByPartNumber(String partNumber);

    @Query("SELECT p FROM SparePart p WHERE p.workspaceId = :workspaceId AND p.stock <= p.minStock ORDER BY p.name ASC")
    List<SparePart> findLowStock(@Param("workspaceId") Long workspaceId);

    @Query("SELECT DISTINCT p.category FROM SparePart p WHERE p.workspaceId = :workspaceId " +
            "AND p.category IS NOT NULL AND p.category <> '' ORDER BY p.category ASC")
    List<String> findDistinctCategories(@Param("workspaceId") Long workspaceId);
}
