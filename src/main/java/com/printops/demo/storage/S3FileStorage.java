package com.printops.demo.storage;

import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

// Bucket privado S3 compatible (Cloudflare R2, AWS S3, MinIO). Lectura solo con URLs
// prefirmadas que vencen (app.storage.url-ttl).
public class S3FileStorage implements FileStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration ttl;

    public S3FileStorage(S3Client client, S3Presigner presigner, String bucket, Duration ttl) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.ttl = ttl;
    }

    @Override
    public String storeImage(String folder, MultipartFile file) {
        String key = StorageKeys.newImageKey(folder, file);
        try (InputStream in = file.getInputStream()) {
            client.putObject(b -> b.bucket(bucket).key(key).contentType(StorageKeys.contentType(key))
                            .cacheControl("private, max-age=31536000, immutable"),
                    RequestBody.fromInputStream(in, file.getSize()));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo subir el archivo.", e);
        }
        return key;
    }

    @Override
    public String urlFor(String key) {
        if (key == null) return null;
        if (StorageKeys.isLegacyUrl(key)) return key;
        return presigner.presignGetObject(r -> r.signatureDuration(ttl)
                        .getObjectRequest(g -> g.bucket(bucket).key(key)))
                .url().toString();
    }

    @Override
    public java.util.Optional<byte[]> read(String key) {
        if (!StorageKeys.isValid(key)) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(client.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray());
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            return java.util.Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        if (!StorageKeys.isValid(key)) return;
        client.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
