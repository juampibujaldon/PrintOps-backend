package com.printops.demo.storage;

import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

// Storage en disco. Las URLs apuntan a /api/files/{clave}?exp=..&sig=.. (FileController):
// la firma HMAC con vencimiento reemplaza al /uploads/** publico, que dejaba ver fotos de
// cualquier taller a quien tuviera el link. En Railway requiere un Volume: sin el, el disco
// del contenedor se borra en cada deploy.
public class LocalFileStorage implements FileStorage {

    private final Path root;
    private final byte[] signingKey;
    private final Duration ttl;
    private final Clock clock;

    public LocalFileStorage(Path root, byte[] signingKey, Duration ttl, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.signingKey = signingKey;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public String storeImage(String folder, MultipartFile file) {
        String key = StorageKeys.newImageKey(folder, file);
        Path target = resolve(key);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el archivo.", e);
        }
        return key;
    }

    @Override
    public String urlFor(String key) {
        if (key == null) return null;
        if (StorageKeys.isLegacyUrl(key)) return key;
        long exp = clock.instant().plus(ttl).getEpochSecond();
        return "/api/files/" + key + "?exp=" + exp + "&sig=" + sign(key, exp);
    }

    @Override
    public void delete(String key) {
        if (!StorageKeys.isValid(key)) return;
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ignored) {
            // Un archivo huerfano no rompe nada; no vale la pena fallar la operacion.
        }
    }

    @Override
    public Optional<byte[]> read(String key) {
        if (!StorageKeys.isValid(key)) return Optional.empty();
        try {
            Path file = resolve(key);
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // Archivo para una URL firmada valida y vigente; vacio si la firma no coincide o vencio.
    public Optional<Path> open(String key, long exp, String sig) {
        if (!StorageKeys.isValid(key) || sig == null) return Optional.empty();
        if (exp < clock.instant().getEpochSecond()) return Optional.empty();
        byte[] expected = sign(key, exp).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
        Path file = resolve(key);
        return Files.isRegularFile(file) ? Optional.of(file) : Optional.empty();
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Clave de archivo inválida.");
        return p;
    }

    private String sign(String key, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            byte[] raw = mac.doFinal((key + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
