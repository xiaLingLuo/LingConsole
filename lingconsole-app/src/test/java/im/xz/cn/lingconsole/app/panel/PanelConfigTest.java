package im.xz.cn.lingconsole.app.panel;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanelConfigTest {

    @Test
    void terminalSecurityDefaultsArePresent() throws Exception {
        PanelConfig config = PanelConfig.load(Files.createTempDirectory("panel-config").resolve("missing.toml"));
        assertEquals(60, config.terminalTicketTtlSeconds());
        assertEquals(1000, config.terminalTicketGlobalLimit());
        assertTrue(PanelConfig.defaultToml().contains("terminalTicketPerIpPerMinute"));
        assertEquals(8192, config.loginBodyMaxBytes());
        assertEquals(2, config.passwordVerificationConcurrency());
        assertEquals(8, config.downloadGlobalLimit());
        assertEquals(4, config.downloadPerNodeLimit());
        assertEquals(2, config.downloadPerUserLimit());
        assertTrue(PanelConfig.defaultToml().contains("maxConcurrentPerNode = 4"));
    }

    @Test
    void rejectsLoginBodyLimitAboveEightKib() throws Exception {
        Path file = Files.createTempFile("panel-login-limit", ".toml");
        Files.writeString(file, "[security]\nloginBodyMaxBytes = 8193\n");
        assertThrows(IllegalArgumentException.class, () -> PanelConfig.load(file));
    }

    @Test
    void rejectsInvalidTicketLimits() throws Exception {
        Path file = Files.createTempFile("panel-config-invalid", ".toml");
        Files.writeString(file, """
                [security]
                terminalTicketGlobalLimit = 2
                terminalTicketPerUserLimit = 3
                """);
        assertThrows(IllegalArgumentException.class, () -> PanelConfig.load(file));
    }

    @Test
    void loadsSocketLimitsAndRejectsInvalidRelationships() throws Exception {
        Path valid = Files.createTempFile("panel-socket-config", ".toml");
        Files.writeString(valid, """
                [socket]
                authenticationTimeoutSeconds = 1
                maxTextMessageBytes = 1024
                maxBinaryMessageBytes = 1024
                maxAggregatedMessageBytes = 1024
                maxUnauthenticatedConnectionsPerIp = 1
                maxConnections = 1
                maxEventsPerSession = 1
                eventRateWindowSeconds = 300
                """);
        PanelConfig config = PanelConfig.load(valid);
        assertEquals(1, config.socketAuthenticationTimeoutSeconds());
        assertEquals(1024, config.socketMaxTextMessageBytes());
        assertEquals(300, config.socketEventRateWindowSeconds());

        Path invalid = Files.createTempFile("panel-socket-invalid", ".toml");
        Files.writeString(invalid, """
                [socket]
                maxTextMessageBytes = 2048
                maxAggregatedMessageBytes = 1024
                """);
        assertThrows(IllegalArgumentException.class, () -> PanelConfig.load(invalid));
    }

    @Test
    void rejectsDownloadScopeLimitAboveGlobalLimit() throws Exception {
        Path file = Files.createTempFile("panel-download-invalid", ".toml");
        Files.writeString(file, """
                [download]
                maxConcurrent = 2
                maxConcurrentPerNode = 3
                """);
        assertThrows(IllegalArgumentException.class, () -> PanelConfig.load(file));
    }
}
