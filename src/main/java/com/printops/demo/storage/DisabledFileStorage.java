package com.printops.demo.storage;

import com.printops.demo.exception.UploadsDisabledException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

// STORAGE_TYPE=none: la subida de imágenes está apagada (no hay bucket ni disco persistente).
// Las fotos y logos ya guardados no se muestran: sus archivos no están en ningún lado.
public class DisabledFileStorage implements FileStorage {

    @Override
    public String storeImage(String folder, MultipartFile file) {
        throw new UploadsDisabledException();
    }

    @Override
    public String urlFor(String key) {
        // Claves heredadas que ya son URLs absolutas apuntan afuera: se devuelven tal cual.
        return key != null && (key.startsWith("http://") || key.startsWith("https://")) ? key : null;
    }

    @Override
    public void delete(String key) {
    }

    @Override
    public Optional<byte[]> read(String key) {
        return Optional.empty();
    }

    @Override
    public boolean uploadsEnabled() {
        return false;
    }
}
