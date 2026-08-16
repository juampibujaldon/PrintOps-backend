// src/main/java/com/printops/demo/repository/PartRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {
    Optional<Part> findByPartNumber(String partNumber);
    boolean existsByPartNumber(String partNumber);
    List<Part> findByNameContainingIgnoreCase(String name);
}
