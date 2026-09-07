package com.codegym.mathclass.storage.strategy;

import com.codegym.mathclass.storage.dto.StoragePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageStrategyTest {

    private LocalStorageStrategy localStorageStrategy;

    @BeforeEach
    void setUp() {
        localStorageStrategy = new LocalStorageStrategy();
    }

    @AfterEach
    void tearDown() throws IOException {
        Path uploadsPath = Paths.get("./uploads");
        if (Files.exists(uploadsPath)) {
            try (var stream = Files.walk(uploadsPath)) {
                stream.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
        }
    }

    @Test
    @DisplayName("Upload tệp hợp lệ sẽ tạo tệp trên đĩa và trả về URL đường dẫn tương đối")
    void upload_ValidFile_CreatesFileOnDiskAndReturnsUrl() throws IOException {
        byte[] content = "Hello World".getBytes();
        String fileName = "sample.png";
        String contentType = "image/png";

        String url = localStorageStrategy.upload(content, fileName, contentType, StoragePolicy.AVATAR);

        assertNotNull(url);
        assertTrue(url.startsWith("/uploads/avatar/"));
        assertTrue(url.endsWith(".png"));

        Path createdFilePath = Paths.get("." + url);
        assertTrue(Files.exists(createdFilePath));
        assertArrayEquals(content, Files.readAllBytes(createdFilePath));
    }

    @Test
    @DisplayName("Xóa tệp hợp lệ theo URL sẽ xóa tệp tương ứng trên đĩa")
    void delete_ValidFileUrl_RemovesFileFromDisk() throws IOException {
        byte[] content = "Delete Test".getBytes();
        String url = localStorageStrategy.upload(content, "test.png", "image/png", StoragePolicy.AVATAR);

        Path createdFilePath = Paths.get("." + url);
        assertTrue(Files.exists(createdFilePath));

        localStorageStrategy.delete(url);

        assertFalse(Files.exists(createdFilePath));
    }

    @Test
    @DisplayName("Xóa tệp với nỗ lực Path Traversal (chứa ..) sẽ bị ngăn chặn và không xóa tệp")
    void delete_PathTraversalAttempt_PreventsDeletionOutsideBaseDir() {
        String pathTraversalUrl = "/uploads/../../important-system-file.txt";

        assertDoesNotThrow(() -> localStorageStrategy.delete(pathTraversalUrl));
    }

    @Test
    @DisplayName("Xóa tệp với URL null hoặc không bắt đầu bằng /uploads/ sẽ bị bỏ qua")
    void delete_InvalidUrl_DoesNothing() {
        assertDoesNotThrow(() -> localStorageStrategy.delete(null));
        assertDoesNotThrow(() -> localStorageStrategy.delete("https://external-site.com/image.png"));
    }
}
