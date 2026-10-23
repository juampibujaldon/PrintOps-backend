package com.printops.demo.storage;

import org.springframework.web.multipart.MultipartFile;

// Almacenamiento de archivos subidos (fotos). En la base se guarda la CLAVE del archivo
// (p. ej. "orders/<uuid>.jpg"), nunca una URL: las URLs se firman al responder y vencen,
// asi una foto no queda accesible para siempre a quien tenga el link.
public interface FileStorage {

    // Guarda la imagen bajo la carpeta dada con un nombre aleatorio y devuelve su clave.
    String storeImage(String folder, MultipartFile file);

    // URL temporal para leer el archivo (null si la clave es null). Claves heredadas que ya
    // son URLs absolutas se devuelven tal cual.
    String urlFor(String key);

    void delete(String key);

    // Contenido del archivo (p. ej. el logo para dibujarlo en el PDF). Vacío si no existe o
    // la clave no es del storage (URLs heredadas: nunca se descargan desde el servidor).
    java.util.Optional<byte[]> read(String key);

    // false con STORAGE_TYPE=none: el front oculta la subida de fotos y logos.
    default boolean uploadsEnabled() {
        return true;
    }
}
