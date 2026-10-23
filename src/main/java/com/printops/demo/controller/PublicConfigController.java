package com.printops.demo.controller;

import com.printops.demo.storage.FileStorage;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

// Funciones que dependen de la configuración del servidor, para que el front oculte lo que
// no está disponible (p. ej. subir fotos sin storage configurado).
@RestController
@RequestMapping("/api/public/config")
public class PublicConfigController {

    private final FileStorage fileStorage;

    public PublicConfigController(FileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    @GetMapping
    public ResponseEntity<Map<String, Boolean>> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                .body(Map.of("uploadsEnabled", fileStorage.uploadsEnabled()));
    }
}
