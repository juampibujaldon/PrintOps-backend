// src/main/java/com/printops/demo/config/WebMvcConfig.java
package com.printops.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get("uploads");
        String uploadPath = uploadDir.toFile().getAbsolutePath();
        // Fotos de ordenes e impresoras: nombre UUID (ver UploadFiles), el contenido de una URL
        // nunca cambia, asi que el cliente puede cachearlo un anio.
        for (String folder : new String[]{"orders", "printers"}) {
            registry.addResourceHandler("/uploads/" + folder + "/**")
                    .addResourceLocations("file:" + uploadPath + "/" + folder + "/")
                    .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
        }
        // Resto (p. ej. PDFs de presupuestos, que se regeneran con el mismo nombre): sin cache largo.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
