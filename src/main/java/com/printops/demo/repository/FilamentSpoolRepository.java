package com.printops.demo.repository;

import com.printops.demo.entity.FilamentSpool;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FilamentSpoolRepository extends JpaRepository<FilamentSpool, Long> {

    @EntityGraph(attributePaths = "printer")
    List<FilamentSpool> findByWorkspaceIdAndArchivedOrderByMaterialAscColorAsc(Long workspaceId, boolean archived);

    @EntityGraph(attributePaths = "printer")
    Optional<FilamentSpool> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
