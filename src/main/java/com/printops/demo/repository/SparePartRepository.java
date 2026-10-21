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
    // Listado con filtros resueltos en SQL. searchPattern es un LIKE ya escapado con '!'
    // ("%" = sin busqueda); category "" = sin filtro. Ver SparePartService.list.
    @Query("""
            SELECT p FROM SparePart p
            WHERE p.workspaceId = :workspaceId
              AND (:searchPattern = '%'
                   OR LOWER(p.name) LIKE LOWER(:searchPattern) ESCAPE '!'
                   OR LOWER(p.partNumber) LIKE LOWER(:searchPattern) ESCAPE '!')
              AND (:category = '' OR LOWER(p.category) = LOWER(:category))
              AND (:lowStock = false OR p.stock <= p.minStock)
            ORDER BY p.name ASC
            """)
    List<SparePart> search(@Param("workspaceId") Long workspaceId,
                           @Param("searchPattern") String searchPattern,
                           @Param("category") String category,
                           @Param("lowStock") boolean lowStock);

    Optional<SparePart> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByPartNumberAndWorkspaceId(String partNumber, Long workspaceId);

    @Query("SELECT p FROM SparePart p WHERE p.workspaceId = :workspaceId AND p.stock <= p.minStock ORDER BY p.name ASC")
    List<SparePart> findLowStock(@Param("workspaceId") Long workspaceId);

    @Query("SELECT DISTINCT p.category FROM SparePart p WHERE p.workspaceId = :workspaceId " +
            "AND p.category IS NOT NULL AND p.category <> '' ORDER BY p.category ASC")
    List<String> findDistinctCategories(@Param("workspaceId") Long workspaceId);
}
