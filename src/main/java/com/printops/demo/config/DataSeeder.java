// src/main/java/com/printops/demo/config/DataSeeder.java
package com.printops.demo.config;

import com.printops.demo.entity.Part;
import com.printops.demo.repository.PartRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Semilla de piezas de ejemplo para el catálogo de stock (US-04).
// Solo inserta si el catálogo está vacío, para no duplicar en cada arranque.
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PartRepository partRepository;

    public DataSeeder(PartRepository partRepository) {
        this.partRepository = partRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (partRepository.count() > 0) {
            return;
        }
        seed("Boquilla 0.4mm", "NOZ-04", 20);
        seed("Boquilla 0.6mm", "NOZ-06", 15);
        seed("Termistor NTC 100K", "THR-NTC100", 12);
        seed("Cartucho calefactor 40W", "HTR-40W", 10);
        seed("Correa GT2 (metro)", "BLT-GT2", 30);
        seed("Rodamiento 608ZZ", "BRG-608ZZ", 50);
        seed("Cama PEI magnética", "BED-PEI", 8);
        seed("Extrusor MK8 completo", "EXT-MK8", 6);
        log.info("Catálogo de piezas inicializado con datos de ejemplo.");
    }

    private void seed(String name, String partNumber, int stock) {
        Part p = new Part();
        p.setName(name);
        p.setPartNumber(partNumber);
        p.setStockQuantity(stock);
        partRepository.save(p);
    }
}
