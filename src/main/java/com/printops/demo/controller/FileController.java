package com.printops.demo.controller;

import com.printops.demo.storage.FileStorage;
import com.printops.demo.storage.LocalFileStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

// Sirve archivos del storage local con URL firmada (ver LocalFileStorage). Es publico a
// nivel de Spring Security porque <img> no manda el header Authorization: la firma con
// vencimiento es la autorizacion. Con storage S3 las URLs van directo al bucket.
@RestController
public class FileController {

    private final FileStorage storage;

    public FileController(FileStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/files/**")
    public ResponseEntity<Resource> get(HttpServletRequest request,
                                        @RequestParam long exp,
                                        @RequestParam String sig) {
        if (!(storage instanceof LocalFileStorage local)) {
            return ResponseEntity.notFound().build();
        }
        String key = request.getRequestURI().substring(request.getContextPath().length() + "/api/files/".length());
        return local.open(key, exp, sig)
                .<ResponseEntity<Resource>>map(path -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(path.getFileName().toString())
                                .orElse(MediaType.APPLICATION_OCTET_STREAM))
                        // La URL ya vence sola; el contenido de una clave nunca cambia.
                        .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                        .body(new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
