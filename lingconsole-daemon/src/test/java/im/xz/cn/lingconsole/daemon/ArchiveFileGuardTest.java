package im.xz.cn.lingconsole.daemon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchiveFileGuardTest {

    @Test
    void countsRegularFilesAndRejectsLimitOverflow() throws Exception {
        Path root = Files.createTempDirectory("archive-input");
        Files.write(root.resolve("one.bin"), new byte[4]);
        Files.createDirectories(root.resolve("nested"));
        Files.write(root.resolve("nested/two.bin"), new byte[6]);

        assertEquals(10, ArchiveFileGuard.totalRegularFileBytes(List.of(root), 10));
        assertThrows(IOException.class,
                () -> ArchiveFileGuard.totalRegularFileBytes(List.of(root), 9));
    }

    @Test
    void checksArchiveFileSizeAndCleansTemporaryTree() throws Exception {
        Path root = Files.createTempDirectory("archive-cleanup");
        Path archive = Files.write(root.resolve("data.7z"), new byte[5]);
        ArchiveFileGuard.checkArchiveFile(archive, 5);
        assertThrows(IOException.class, () -> ArchiveFileGuard.checkArchiveFile(archive, 4));

        Path temporary = Files.createDirectories(root.resolve("temporary/nested"));
        Files.writeString(temporary.resolve("file.txt"), "data");
        ArchiveFileGuard.deleteTree(root.resolve("temporary"));
        assertFalse(Files.exists(root.resolve("temporary")));
    }
}
