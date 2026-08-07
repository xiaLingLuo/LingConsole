/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package im.xz.cn.lingconsole.app.panel;

import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.config.TomlConfig;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


public class PanelConfig extends TomlConfig {

    private final String host;
    private final int port;
    private final int sessionTimeout;
    private final int maxLoginAttempts;
    private final int lockoutDuration;
    private final int rateLimitPerSecond;
    private final int loginBodyMaxBytes;
    private final int loginMaxConcurrent;
    private final int passwordVerificationConcurrency;
    private final int passwordVerificationTimeoutMillis;
    private final String firstLaunchPasswordFile;
    private final int terminalTicketTtlSeconds;
    private final int terminalTicketGlobalLimit;
    private final int terminalTicketPerUserLimit;
    private final int terminalTicketPerIpPerMinute;
    private final int downloadGlobalLimit;
    private final int downloadPerNodeLimit;
    private final int downloadPerUserLimit;
    private final List<String> externalOrigins;
    private final List<String> trustedHosts;
    private final int socketAuthenticationTimeoutSeconds;
    private final int socketMaxTextMessageBytes;
    private final int socketMaxBinaryMessageBytes;
    private final int socketMaxAggregatedMessageBytes;
    private final int socketMaxUnauthenticatedConnectionsPerIp;
    private final int socketMaxConnections;
    private final int socketMaxEventsPerSession;
    private final int socketEventRateWindowSeconds;
    private final String theme;
    private final String language;
    private final String dbPath;

    private PanelConfig(TomlParseResult r) {
        super(r);
        host = str("server.host", "0.0.0.0");
        port = intVal("server.port", Constants.DEFAULT_WEB_PORT);
        sessionTimeout = intVal("auth.sessionTimeout", 3600);
        maxLoginAttempts = intVal("auth.maxLoginAttempts", 5);
        lockoutDuration = intVal("auth.lockoutDuration", 900);
        rateLimitPerSecond = intVal("security.rateLimitPerSecond", 8);
        loginBodyMaxBytes = ranged("security.loginBodyMaxBytes", 8192, 1024, 8192);
        loginMaxConcurrent = ranged("security.loginMaxConcurrent", 8, 1, 256);
        passwordVerificationConcurrency = ranged("security.passwordVerificationConcurrency", 2, 1, 32);
        passwordVerificationTimeoutMillis = ranged("security.passwordVerificationTimeoutMillis", 250, 10, 5000);
        firstLaunchPasswordFile = str("security.firstLaunchPasswordFile",
                Constants.DATA_DIR + "/first-launch-password.txt");
        terminalTicketTtlSeconds = ranged("security.terminalTicketTtlSeconds", 60, 1, 600);
        terminalTicketGlobalLimit = ranged("security.terminalTicketGlobalLimit", 1000, 1, 100_000);
        terminalTicketPerUserLimit = ranged("security.terminalTicketPerUserLimit", 10, 1, 1000);
        terminalTicketPerIpPerMinute = ranged("security.terminalTicketPerIpPerMinute", 10, 1, 10_000);
        if (terminalTicketPerUserLimit > terminalTicketGlobalLimit) {
            throw new IllegalArgumentException("security.terminalTicketPerUserLimit 不能大于 terminalTicketGlobalLimit");
        }
        downloadGlobalLimit = ranged("download.maxConcurrent", 8, 1, 10_000);
        downloadPerNodeLimit = ranged("download.maxConcurrentPerNode", 4, 1, 10_000);
        downloadPerUserLimit = ranged("download.maxConcurrentPerUser", 2, 1, 10_000);
        if (downloadPerNodeLimit > downloadGlobalLimit || downloadPerUserLimit > downloadGlobalLimit) {
            throw new IllegalArgumentException("download 每节点/每用户并发限制不能大于全局限制");
        }
        externalOrigins = List.copyOf(strList("security.externalOrigins", List.of()));
        trustedHosts = List.copyOf(strList("security.trustedHosts", List.of()));
        externalOrigins.forEach(PanelConfig::validateExternalOrigin);
        trustedHosts.forEach(PanelConfig::validateTrustedHost);
        socketAuthenticationTimeoutSeconds = ranged("socket.authenticationTimeoutSeconds", 10, 1, 300);
        socketMaxTextMessageBytes = ranged("socket.maxTextMessageBytes", 1024 * 1024, 1024, 64 * 1024 * 1024);
        socketMaxBinaryMessageBytes = ranged("socket.maxBinaryMessageBytes", 1024 * 1024, 1024, 64 * 1024 * 1024);
        socketMaxAggregatedMessageBytes = ranged("socket.maxAggregatedMessageBytes", 4 * 1024 * 1024, 1024, 64 * 1024 * 1024);
        if (socketMaxAggregatedMessageBytes < Math.max(socketMaxTextMessageBytes, socketMaxBinaryMessageBytes)) {
            throw new IllegalArgumentException("socket.maxAggregatedMessageBytes 不能小于文本或二进制消息限制");
        }
        socketMaxUnauthenticatedConnectionsPerIp = ranged(
                "socket.maxUnauthenticatedConnectionsPerIp", 20, 1, 10_000);
        socketMaxConnections = ranged("socket.maxConnections", 10_000, 1, 1_000_000);
        if (socketMaxUnauthenticatedConnectionsPerIp > socketMaxConnections) {
            throw new IllegalArgumentException("socket.maxUnauthenticatedConnectionsPerIp 不能大于 maxConnections");
        }
        socketMaxEventsPerSession = ranged("socket.maxEventsPerSession", 100, 1, 100_000);
        socketEventRateWindowSeconds = ranged("socket.eventRateWindowSeconds", 1, 1, 300);
        theme = str("web.theme", "default");
        language = str("web.language", "zh_CN");
        dbPath = str("database.path", Constants.WEB_DIR + "/data/lingconsole.db");
    }

    public static PanelConfig load(Path path) throws IOException {
        if (Files.exists(path)) {
            return new PanelConfig(parse(path));
        }
        return new PanelConfig(Toml.parse(""));
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int sessionTimeout() {
        return sessionTimeout;
    }

    public int maxLoginAttempts() {
        return maxLoginAttempts;
    }

    public int lockoutDuration() {
        return lockoutDuration;
    }

    public int rateLimitPerSecond() {
        return rateLimitPerSecond;
    }

    public int loginBodyMaxBytes() { return loginBodyMaxBytes; }
    public int loginMaxConcurrent() { return loginMaxConcurrent; }
    public int passwordVerificationConcurrency() { return passwordVerificationConcurrency; }
    public int passwordVerificationTimeoutMillis() { return passwordVerificationTimeoutMillis; }
    public String firstLaunchPasswordFile() { return firstLaunchPasswordFile; }

    public int terminalTicketTtlSeconds() { return terminalTicketTtlSeconds; }
    public int terminalTicketGlobalLimit() { return terminalTicketGlobalLimit; }
    public int terminalTicketPerUserLimit() { return terminalTicketPerUserLimit; }
    public int terminalTicketPerIpPerMinute() { return terminalTicketPerIpPerMinute; }
    public int downloadGlobalLimit() { return downloadGlobalLimit; }
    public int downloadPerNodeLimit() { return downloadPerNodeLimit; }
    public int downloadPerUserLimit() { return downloadPerUserLimit; }
    public List<String> externalOrigins() { return externalOrigins; }
    public List<String> trustedHosts() { return trustedHosts; }
    public int socketAuthenticationTimeoutSeconds() { return socketAuthenticationTimeoutSeconds; }
    public int socketMaxTextMessageBytes() { return socketMaxTextMessageBytes; }
    public int socketMaxBinaryMessageBytes() { return socketMaxBinaryMessageBytes; }
    public int socketMaxAggregatedMessageBytes() { return socketMaxAggregatedMessageBytes; }
    public int socketMaxUnauthenticatedConnectionsPerIp() { return socketMaxUnauthenticatedConnectionsPerIp; }
    public int socketMaxConnections() { return socketMaxConnections; }
    public int socketMaxEventsPerSession() { return socketMaxEventsPerSession; }
    public int socketEventRateWindowSeconds() { return socketEventRateWindowSeconds; }

    public String theme() {
        return theme;
    }

    public String language() {
        return language;
    }

    public String dbPath() {
        return dbPath;
    }

    public static String defaultToml() {
        return """
                [server]
                host = "0.0.0.0"
                port = 55600

                [auth]
                sessionTimeout = 3600
                maxLoginAttempts = 5
                lockoutDuration = 900

                [security]
                rateLimitPerSecond = 8
                loginBodyMaxBytes = 8192
                loginMaxConcurrent = 8
                passwordVerificationConcurrency = 2
                passwordVerificationTimeoutMillis = 250
                firstLaunchPasswordFile = "/lingConsole/first-launch-password.txt"
                terminalTicketTtlSeconds = 60
                terminalTicketGlobalLimit = 1000
                terminalTicketPerUserLimit = 10
                terminalTicketPerIpPerMinute = 10
                externalOrigins = []
                trustedHosts = []

                [download]
                maxConcurrent = 8
                maxConcurrentPerNode = 4
                maxConcurrentPerUser = 2

                [socket]
                authenticationTimeoutSeconds = 10
                maxTextMessageBytes = 1048576
                maxBinaryMessageBytes = 1048576
                maxAggregatedMessageBytes = 4194304
                maxUnauthenticatedConnectionsPerIp = 20
                maxConnections = 10000
                maxEventsPerSession = 100
                eventRateWindowSeconds = 1

                [web]
                theme = "default"
                language = "zh_CN"

                [database]
                path = "/lingConsole/web/data/lingconsole.db"
                """;
    }

    private int ranged(String key, int def, int min, int max) {
        int value = intVal(key, def);
        if (value < min || value > max) {
            throw new IllegalArgumentException(key + " 必须在 " + min + " 到 " + max + " 之间");
        }
        return value;
    }

    private static void validateExternalOrigin(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("security.externalOrigins 包含无效 origin: " + value);
        }
    }

    private static void validateTrustedHost(String value) {
        try {
            if (value == null || value.isBlank() || value.contains("/") || value.contains("@")) {
                throw new IllegalArgumentException();
            }
            java.net.URI uri = java.net.URI.create("http://" + value);
            if (uri.getHost() == null) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("security.trustedHosts 包含无效 Host: " + value);
        }
    }
}
