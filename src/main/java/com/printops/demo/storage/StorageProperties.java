package com.printops.demo.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, Duration urlTtl, String localDir, S3 s3) {

    public record S3(String bucket, String endpoint, String region, String accessKey, String secretKey) {
    }

    public Duration urlTtl() {
        return urlTtl != null ? urlTtl : Duration.ofHours(1);
    }
}
