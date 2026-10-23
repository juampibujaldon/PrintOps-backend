package com.printops.demo.repository;

import com.printops.demo.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByWorkspaceIdOrderByNameAsc(Long workspaceId);

    Optional<Supplier> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
