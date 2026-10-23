package com.printops.demo.storage;

import com.printops.demo.util.UploadFiles;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Reglas comunes de claves: carpeta conocida + nombre aleatorio generado por el servidor.
final class StorageKeys {

    static final Set<String> FOLDERS = Set.of("orders", "printers");
    private static final Pattern KEY = Pattern.compile("^[a-z]+/[0-9a-f-]{36}\\.[a-z]{3,4}$");

    private StorageKeys() {
    }

    static String newImageKey(String folder, MultipartFile file) {
        if (!FOLDERS.contains(folder)) {
            throw new IllegalArgumentException("Carpeta de storage desconocida: " + folder);
        }
        return folder + "/" + UploadFiles.safeImageFileName(file);
    }

    // Solo claves generadas por newImageKey: impide leer rutas arbitrarias ("../", etc.).
    static boolean isValid(String key) {
        return key != null && KEY.matcher(key).matches() && FOLDERS.contains(key.substring(0, key.indexOf('/')));
    }

    static boolean isLegacyUrl(String key) {
        return key.startsWith("http://") || key.startsWith("https://") || key.startsWith("/");
    }

    static String contentType(String key) {
        String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            default -> "image/jpeg";
        };
    }
}
