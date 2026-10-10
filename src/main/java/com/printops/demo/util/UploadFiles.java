package com.printops.demo.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

// Nombres de archivo seguros para fotos subidas. Nunca se usa el nombre original
// del cliente (podía contener "../" y escribir fuera del directorio de uploads).
public final class UploadFiles {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "heic", "heif", "gif");

    private UploadFiles() {
    }

    public static String safeImageFileName(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new IllegalArgumentException("Solo se permiten imágenes.");
        }
        String ext = "jpg";
        String original = file.getOriginalFilename();
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && dot < original.length() - 1) {
                String candidate = original.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (IMAGE_EXTENSIONS.contains(candidate)) {
                    ext = candidate;
                }
            }
        }
        return UUID.randomUUID() + "." + ext;
    }
}
