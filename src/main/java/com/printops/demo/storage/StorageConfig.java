package com.printops.demo.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    FileStorage fileStorage(StorageProperties props, @Value("${jwt.secret}") String jwtSecret) {
        if ("s3".equalsIgnoreCase(props.type())) {
            return s3(props);
        }
        return new LocalFileStorage(Path.of(props.localDir() != null ? props.localDir() : "uploads"),
                deriveKey(jwtSecret), props.urlTtl(), Clock.systemUTC());
    }

    private FileStorage s3(StorageProperties props) {
        StorageProperties.S3 s3 = props.s3();
        if (s3 == null || isBlank(s3.bucket()) || isBlank(s3.accessKey()) || isBlank(s3.secretKey())) {
            throw new IllegalStateException(
                    "STORAGE_TYPE=s3 requiere S3_BUCKET, S3_ACCESS_KEY y S3_SECRET_KEY (y S3_ENDPOINT para R2).");
        }
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
        Region region = Region.of(isBlank(s3.region()) ? "auto" : s3.region());
        // R2: path-style, sin chunked encoding y checksums solo cuando son obligatorios
        // (R2 rechaza los CRC32 que el SDK agrega por defecto).
        S3Configuration serviceConfig = S3Configuration.builder()
                .pathStyleAccessEnabled(true).chunkedEncodingEnabled(false).build();

        var clientBuilder = S3Client.builder().credentialsProvider(credentials).region(region)
                .serviceConfiguration(serviceConfig)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        var presignerBuilder = S3Presigner.builder().credentialsProvider(credentials).region(region)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (!isBlank(s3.endpoint())) {
            clientBuilder.endpointOverride(URI.create(s3.endpoint()));
            presignerBuilder.endpointOverride(URI.create(s3.endpoint()));
        }
        return new S3FileStorage(clientBuilder.build(), presignerBuilder.build(), s3.bucket(), props.urlTtl());
    }

    // Clave de firma de URLs derivada del secreto JWT (otra clave, mismo secreto de entorno).
    private static byte[] deriveKey(String jwtSecret) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("printops-files:" + jwtSecret).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
