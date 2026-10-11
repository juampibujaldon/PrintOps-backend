// src/main/java/com/printops/demo/entity/ComponentQuality.java
package com.printops.demo.entity;

// Calidad del repuesto instalado: escala la vida útil base del componente.
// CUSTOM usa las horas que carga el usuario.
public enum ComponentQuality {
    ECONOMY(0.6),
    STANDARD(1.0),
    PREMIUM(1.6),
    CUSTOM(1.0);

    private final double multiplier;

    ComponentQuality(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
