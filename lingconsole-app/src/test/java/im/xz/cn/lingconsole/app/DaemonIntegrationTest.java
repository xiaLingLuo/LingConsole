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
package im.xz.cn.lingconsole.app;

import com.fasterxml.jackson.databind.JsonNode;
import im.xz.cn.lingconsole.common.socketio.SocketIOClient;
import im.xz.cn.lingconsole.daemon.DaemonApp;
import im.xz.cn.lingconsole.daemon.DaemonConfig;
import im.xz.cn.lingconsole.testutil.TestUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DaemonIntegrationTest {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    @TempDir
    static Path dataDir;

    static int daemonPort;
    static DaemonApp daemonApp;

    @BeforeAll
    static void setUp() throws Exception {
        daemonPort = TestUtil.freePort();
        DaemonConfig config = TestUtil.writeDaemonConfig(dataDir, daemonPort);
        daemonApp = new DaemonApp(config);
        daemonApp.start();
    }

    @AfterAll
    static void tearDown() {
        if (daemonApp != null) {
            daemonApp.stop();
        }
    }

    private SocketIOClient connect() throws Exception {
        SocketIOClient client = new SocketIOClient("127.0.0.1", daemonPort, "/daemon");
        assertTrue(client.connect(), "Socket.IO 连接失败");
        return client;
    }

    @Test
    void authRejectsWrongKey() throws Exception {
        SocketIOClient client = connect();
        Object resp = client.requestBlocking("auth", Map.of("key", "wrong-key"), 5000);
        assertEquals(401, ((JsonNode) resp).path("status").asInt());
        client.disconnect();
    }

    @Test
    void unauthenticatedEventRejected() throws Exception {
        SocketIOClient client = connect();
        
        Object resp = client.requestBlocking("app:list", Map.of(), 5000);
        assertEquals(401, ((JsonNode) resp).path("status").asInt());
        client.disconnect();
    }

    @Test
    void appFullLifecycle() throws Exception {
        SocketIOClient client = connect();
        
        Object auth = client.requestBlocking("auth", Map.of("key", TestUtil.TEST_DAEMON_KEY), 5000);
        assertEquals(200, ((JsonNode) auth).path("status").asInt());

        
        String command = isWindows() ? "ping -n 30 127.0.0.1" : "sleep 30";
        Map<String, Object> createData = new LinkedHashMap<>();
        createData.put("id", "ittestapp");
        createData.put("name", "it-test-app");
        createData.put("command", command);
        createData.put("type", "general");
        Object createResp = client.requestBlocking("app:create", createData, 8000);
        JsonNode created = (JsonNode) createResp;
        assertEquals(200, created.path("status").asInt());
        String appId = created.path("data").path("id").asText();
        assertNotNull(appId);

        
        Object listResp = client.requestBlocking("app:list", Map.of(), 8000);
        JsonNode list = (JsonNode) listResp;
        assertEquals(200, list.path("status").asInt());
        assertEquals(1, list.path("data").size());

        
        Object startResp = client.requestBlocking("app:start", Map.of("id", appId), 8000);
        JsonNode started = (JsonNode) startResp;
        assertEquals(200, started.path("status").asInt());
        assertEquals(3, started.path("data").path("status").asInt()); 
        assertTrue(started.path("data").path("pid").asLong() > 0);

        
        Object statusResp = client.requestBlocking("app:status", Map.of("id", appId), 8000);
        JsonNode status = (JsonNode) statusResp;
        assertEquals(3, status.path("data").path("status").asInt());

        
        Object logResp = client.requestBlocking("app:log", Map.of("id", appId, "count", 20), 8000);
        JsonNode log = (JsonNode) logResp;
        assertEquals(200, log.path("status").asInt());

        
        Object stopResp = client.requestBlocking("app:stop", Map.of("id", appId), 8000);
        JsonNode stopped = (JsonNode) stopResp;
        assertEquals(200, stopped.path("status").asInt());
        assertEquals(0, stopped.path("data").path("status").asInt());

        
        Object deleteResp = client.requestBlocking("app:delete", Map.of("id", appId), 8000);
        assertEquals(200, ((JsonNode) deleteResp).path("status").asInt());

        client.disconnect();
    }

    @Test
    void monitorStatsWorks() throws Exception {
        SocketIOClient client = connect();
        client.requestBlocking("auth", Map.of("key", TestUtil.TEST_DAEMON_KEY), 5000);
        Object resp = client.requestBlocking("monitor:stats", Map.of(), 8000);
        JsonNode node = (JsonNode) resp;
        assertEquals(200, node.path("status").asInt());
        assertTrue(node.path("data").path("cpuUsage").asDouble() >= 0);
        assertTrue(node.path("data").path("memoryTotal").asLong() > 0);
        client.disconnect();
    }

    @Test
    void fileOperationsWork() throws Exception {
        SocketIOClient client = connect();
        client.requestBlocking("auth", Map.of("key", TestUtil.TEST_DAEMON_KEY), 5000);

        
        String base = dataDir.resolve("filetest").toString();
        String encBase = java.net.URLEncoder.encode(base, java.nio.charset.StandardCharsets.UTF_8);
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();

        
        java.net.http.HttpRequest mkdir = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/files/mkdir?path=" + encBase))
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
        var mkdirResp = http.send(mkdir, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, mkdirResp.statusCode());

        java.net.http.HttpRequest write = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/files/write"))
                .header("Content-Type", "application/json")
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"path\":\"" + base.replace("\\", "/") + "/hello.txt\",\"content\":\"hello\"}"))
                .build();
        assertEquals(200, http.send(write, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());

        
        java.net.http.HttpRequest noKey = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps"))
                .GET().build();
        assertEquals(401, http.send(noKey, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());

        client.disconnect();
    }

    @Test
    void execEndpointRunsCommand() throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        String body = "{\"command\":\"echo lingconsole-exec-test\",\"timeoutMs\":5000}";
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/exec"))
                .header("Content-Type", "application/json")
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build();
        var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        JsonNode data = MAPPER.readTree(resp.body()).path("data");
        assertEquals(0, data.path("exitCode").asInt());
        assertTrue(data.path("stdout").asText().contains("lingconsole-exec-test"));
    }

    @Test
    void execEndpointRejectsEmptyCommand() throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/exec"))
                .header("Content-Type", "application/json")
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"command\":\"\",\"timeoutMs\":1000}"))
                .build();
        var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(400, resp.statusCode());
    }

    @Test
    void appSignalEndpointExistsAndBehaves() throws Exception {
        
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        String createBody = "{\"id\":\"sigapp\",\"name\":\"sig-app\",\"command\":\""
                + (isWindows() ? "ping -n 20 127.0.0.1" : "sleep 20") + "\"}";
        java.net.http.HttpRequest create = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps"))
                .header("Content-Type", "application/json")
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(createBody))
                .build();
        JsonNode created = MAPPER.readTree(http.send(create, java.net.http.HttpResponse.BodyHandlers.ofString()).body())
                .path("data");
        String appId = created.path("id").asText();
        assertNotNull(appId);
        
        java.net.http.HttpRequest start = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps/" + appId + "/start"))
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();
        http.send(start, java.net.http.HttpResponse.BodyHandlers.ofString());
        Thread.sleep(300);

        java.net.http.HttpRequest signal = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps/" + appId + "/signal"))
                .header("Content-Type", "application/json")
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"signal\":\"SIGTERM\"}"))
                .build();
        var signalResp = http.send(signal, java.net.http.HttpResponse.BodyHandlers.ofString());
        int code = signalResp.statusCode();
        
        assertTrue(code == 200 || code == 400, "signal 状态应为 200 或 400, 实际 " + code);

        
        java.net.http.HttpRequest stop = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps/" + appId + "/stop"))
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                .build();
        http.send(stop, java.net.http.HttpResponse.BodyHandlers.ofString());
        Thread.sleep(600);

        
        java.net.http.HttpRequest del = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps/" + appId))
                .header("X-LingConsole-Key", TestUtil.TEST_DAEMON_KEY)
                .DELETE()
                .build();
        http.send(del, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
