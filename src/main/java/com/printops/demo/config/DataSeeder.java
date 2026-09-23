// src/main/java/com/printops/demo/config/DataSeeder.java
package com.printops.demo.config;

import com.printops.demo.entity.SparePart;
import com.printops.demo.repository.SparePartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Semilla de repuestos de ejemplo para el inventario (US-10).
// Solo inserta si el catálogo está vacío, para no duplicar en cada arranque.
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SparePartRepository sparePartRepository;

    public DataSeeder(SparePartRepository sparePartRepository) {
        this.sparePartRepository = sparePartRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (sparePartRepository.count() > 0) {
            return;
        }
        seed("Boquilla 0.4mm", "NOZ-04", "Extrusión", 20, 5, 12.5);
        seed("Boquilla 0.6mm", "NOZ-06", "Extrusión", 15, 5, 14.0);
        seed("Termistor NTC 100K", "THR-NTC100", "Electrónica", 12, 3, 8.9);
        seed("Cartucho calefactor 40W", "HTR-40W", "Electrónica", 10, 3, 18.5);
        seed("Correa GT2 6mm (metro)", "BLT-GT2", "Movimiento", 30, 5, 4.2);
        seed("Rodamiento 608ZZ", "BRG-608ZZ", "Movimiento", 50, 10, 1.8);
        seed("Cama PEI magnética", "BED-PEI", "Extrusión", 8, 2, 45.0);
        seed("Extrusor MK8 completo", "EXT-MK8", "Extrusión", 6, 1, 89.0);
        log.info("Catálogo de repuestos inicializado con datos de ejemplo.");
    }

    private void seed(String name, String partNumber, String category, int stock, int minStock, double unitPrice) {
        SparePart p = new SparePart();
        p.setName(name);
        p.setPartNumber(partNumber);
        p.setCategory(category);
        p.setStock(stock);
        p.setMinStock(minStock);
        p.setUnitPrice(unitPrice);
        p.setWorkspaceId(1L);
        sparePartRepository.save(p);
    }
}
