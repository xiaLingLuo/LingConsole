package im.xz.cn.lingconsole.daemon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DaemonConfigTest {

    @Test
    void loadsConfiguredLimitsAtBoundaries() throws Exception {
        Path file = Files.createTempFile("daemon-config", ".toml");
        Files.writeString(file, """
                [auth]
                authTimeout = 1
                successUnlockOnceEnabled = true
                [instance]
                maxFileTasks = 64
                outputBufferSize = 100000
                [archive.compress]
                maxEntries = 1
                maxTotalBytes = 1048576
                timeoutSeconds = 1
                [archive.extract]
                maxEntries = 10000000
                maxTotalBytes = 109951162777600
                timeoutSeconds = 86400
                [socket]
                maxTextMessageBytes = 1024
                maxBinaryMessageBytes = 1024
                maxAggregatedMessageBytes = 1024
                maxUnauthenticatedConnectionsPerIp = 1
                maxConnections = 1
                maxEventsPerSession = 100000
                eventRateWindowSeconds = 300
                """);

        DaemonConfig config = DaemonConfig.load(file);
        assertEquals(1, config.authTimeout());
        assertTrue(config.successUnlockOnceEnabled());
        assertEquals(64, config.maxFileTasks());
        assertEquals(1, config.archiveCompress().maxEntries());
        assertEquals(1_048_576, config.archiveCompress().maxTotalBytes());
        assertEquals(10_000_000, config.archiveExtract().maxEntries());
        assertEquals(100_000, config.outputBufferSize());
        assertEquals(1024, config.socketMaxAggregatedMessageBytes());
        assertEquals(300, config.socketEventRateWindowSeconds());
        assertTrue(DaemonConfig.defaultToml().contains("[archive.compress]"));
        assertTrue(DaemonConfig.defaultToml().contains("successUnlockOnceEnabled = false"));
    }

    @Test
    void successUnlockOnceIsDisabledByDefault() throws Exception {
        Path file = Files.createTempFile("daemon-config-default", ".toml");
        Files.writeString(file, "");

        assertFalse(DaemonConfig.load(file).successUnlockOnceEnabled());
    }

    @Test
    void rejectsOutOfRangeAndInconsistentLimits() throws Exception {
        assertInvalid("[instance]\nmaxFileTasks = 0\n");
        assertInvalid("[instance]\noutputBufferSize = 100001\n");
        assertInvalid("[archive.compress]\nmaxEntries = 0\n");
        assertInvalid("[archive.extract]\nmaxTotalBytes = 1048575\n");
        assertInvalid("[auth]\nauthTimeout = 301\n");
        assertInvalid("[socket]\nmaxTextMessageBytes = 2048\nmaxAggregatedMessageBytes = 1024\n");
        assertInvalid("[socket]\nmaxUnauthenticatedConnectionsPerIp = 2\nmaxConnections = 1\n");
    }

    @Test
    void daemonKeyStatusUsesNineCharacterQualificationBoundary() throws Exception {
        assertEquals(DaemonConfig.KeyStatus.EMPTY, loadKey("").keyStatus());
        assertEquals(DaemonConfig.KeyStatus.EMPTY, loadKey("   ").keyStatus());
        assertEquals(DaemonConfig.KeyStatus.PLACEHOLDER,
                loadKey(DaemonConfig.AUTO_KEY_PLACEHOLDER).keyStatus());
        assertEquals(DaemonConfig.KeyStatus.TOO_SHORT, loadKey("12345678").keyStatus());
        assertEquals(DaemonConfig.KeyStatus.QUALIFIED, loadKey("123456789").keyStatus());
    }

    @Test
    void authManagerRejectsBlankCredentialsEvenWhenConfiguredKeyIsBlank() {
        assertFalse(new AuthManager("").verify(""));
        assertFalse(new AuthManager("   ").verify("   "));
        assertFalse(new AuthManager("12345678").verify(""));
        assertTrue(new AuthManager("12345678").verify("12345678"));
    }

    private static DaemonConfig loadKey(String key) throws Exception {
        Path file = Files.createTempFile("daemon-key", ".toml");
        Files.writeString(file, "[auth]\nkey = \"" + key + "\"\n");
        return DaemonConfig.load(file);
    }

    private static void assertInvalid(String toml) throws Exception {
        Path file = Files.createTempFile("daemon-config-invalid", ".toml");
        Files.writeString(file, toml);
        assertThrows(IllegalArgumentException.class, () -> DaemonConfig.load(file));
    }
}
