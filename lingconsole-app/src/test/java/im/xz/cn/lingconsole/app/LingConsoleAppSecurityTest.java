package im.xz.cn.lingconsole.app;

import im.xz.cn.lingconsole.daemon.DaemonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LingConsoleAppSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void daemonKeyWritePreservesConfigAndProducesCompleteToml() throws Exception {
        Path config = tempDir.resolve("config.toml");
        Files.writeString(config, "[server]\nport = 55701\n[auth]\nkey = \"\"\nname = \"node\"\n");

        LingConsoleApp.writeDaemonKeyAtomically(config, "123456789");

        DaemonConfig loaded = DaemonConfig.load(config);
        assertEquals(55701, loaded.port());
        assertEquals("node", loaded.name());
        assertEquals("123456789", loaded.key());
    }

    @Test
    void firstPasswordFileUsesCreateNewAndDoesNotOverwrite() throws Exception {
        Path file = tempDir.resolve("first-launch-password.txt");
        assertTrue(LingConsoleApp.writeFirstLaunchPassword(file, "first-secret"));
        String original = Files.readString(file);

        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertEquals(PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(file));
        } else if (Files.getFileStore(file).supportsFileAttributeView("acl")) {
            var acl = Files.getFileAttributeView(file, AclFileAttributeView.class).getAcl();
            assertEquals(2, acl.size());
            String username = System.getProperty("user.name").toUpperCase();
            assertTrue(acl.stream().anyMatch(entry -> {
                String principal = entry.principal().getName().toUpperCase();
                return principal.equals(username) || principal.endsWith("\\" + username);
            }));
            assertTrue(acl.stream().anyMatch(entry -> entry.principal().getName()
                    .toUpperCase().endsWith("SYSTEM")));
        }

        assertFalse(LingConsoleApp.writeFirstLaunchPassword(file, "replacement-secret"));
        assertEquals(original, Files.readString(file));
        assertFalse(original.contains("replacement-secret"));
    }
}
