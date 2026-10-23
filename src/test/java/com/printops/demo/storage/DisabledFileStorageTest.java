package com.printops.demo.storage;

import com.printops.demo.exception.UploadsDisabledException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisabledFileStorageTest {

    private final FileStorage storage = new StorageConfig().fileStorage(
            new StorageProperties("none", Duration.ofHours(1), null, null), "secreto");

    @Test
    void noneTypeRejectsUploadsAndHidesStoredFiles() {
        assertThat(storage.uploadsEnabled()).isFalse();
        assertThatThrownBy(() -> storage.storeImage("printers",
                new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1, 2, 3})))
                .isInstanceOf(UploadsDisabledException.class);
        assertThat(storage.urlFor("printers/abc.jpg")).isNull();
        assertThat(storage.urlFor("https://cdn.example/x.jpg")).isEqualTo("https://cdn.example/x.jpg");
        assertThat(storage.read("logos/abc.png")).isEmpty();
        storage.delete("printers/abc.jpg");
    }
}
