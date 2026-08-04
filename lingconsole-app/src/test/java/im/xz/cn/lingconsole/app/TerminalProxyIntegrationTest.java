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
import com.fasterxml.jackson.databind.ObjectMapper;
import im.xz.cn.lingconsole.app.panel.PanelConfig;
import im.xz.cn.lingconsole.app.panel.PanelServer;
import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.app.panel.repository.RootAccountRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserRepository;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.common.socketio.SocketIOClient;
import im.xz.cn.lingconsole.daemon.DaemonApp;
import im.xz.cn.lingconsole.daemon.DaemonConfig;
import im.xz.cn.lingconsole.testutil.TestUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class TerminalProxyIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    static Path dataDir;

    static int panelPort;
    static int daemonPort;
    static DaemonApp daemonApp;
    static PanelServer panelServer;
    static DatabaseManager db;
    static String rootPw;

    static HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() throws Exception {
        daemonPort = TestUtil.freePort();
        panelPort = TestUtil.freePort();

        DaemonConfig daemonConfig = TestUtil.writeDaemonConfig(dataDir, daemonPort);
        daemonApp = new DaemonApp(daemonConfig);
        daemonApp.start();

        PanelConfig panelConfig = TestUtil.writePanelConfig(dataDir, panelPort);
        db = new DatabaseManager(panelConfig.dbPath());
        UserService userService = new UserService(new UserRepository(db), new RootAccountRepository(db));
        rootPw = userService.firstLaunchInit();

        panelServer = new PanelServer(panelConfig, db, daemonConfig, dataDir.toString(), false,
                new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(),
                new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry());
        panelServer.start();
    }

    @AfterAll
    static void tearDown() {
        if (panelServer != null) {
            panelServer.stop();
        }
        if (daemonApp != null) {
            daemonApp.stop();
        }
        if (db != null) {
            db.close();
        }
    }

    private String loginCookie() throws Exception {
        String body = MAPPER.writeValueAsString(Map.of("username", "ling", "password", rootPw));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        String setCookie = resp.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.startsWith("ling_session="), "应下发会话 Cookie");
        return setCookie.split(";")[0];
    }

    private String nodeId(String cookie) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/nodes"))
                .header("Cookie", cookie)
                .GET().build();
        JsonNode nodes = MAPPER.readTree(http.send(req, HttpResponse.BodyHandlers.ofString()).body()).path("data");
        return nodes.get(0).path("id").asText();
    }

    private String requestTicket(String cookie, String nodeId) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort
                        + "/api/nodes/" + nodeId + "/terminal/passport"))
                .header("Cookie", cookie)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"cols\":80,\"rows\":24}"))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        return MAPPER.readTree(resp.body()).path("data").path("ticket").asText();
    }

    @Test
    void terminalStreamProxiedThroughPanel() throws Exception {
        String cookie = loginCookie();
        String nodeId = nodeId(cookie);
        String ticket = requestTicket(cookie, nodeId);

        SocketIOClient panel = new SocketIOClient("127.0.0.1", panelPort, "/panel", false,
                java.time.Duration.ofSeconds(5)).cookie(cookie);

        CompletableFuture<Boolean> authed = new CompletableFuture<>();
        CompletableFuture<Boolean> connected = new CompletableFuture<>();
        CompletableFuture<String> output = new CompletableFuture<>();

        panel.on("auth", (c, e, d) -> authed.complete(d instanceof JsonNode n && n.path("status").asInt() == 200));
        panel.on("terminal:connect", (c, e, d) -> connected.complete(
                d instanceof JsonNode n && n.path("status").asInt() == 200));
        panel.on("terminal:output", (c, e, d) -> {
            if (d instanceof JsonNode n) {
                String text = n.path("data").asText("");
                if (!output.isDone() && text.contains("PROXY_OK")) {
                    output.complete(text);
                }
            }
        });

        assertTrue(panel.connect(), "面板 Socket 连接失败");
        assertTrue(authed.get(5, TimeUnit.SECONDS), "面板 Socket 应通过 Cookie 认证");

        panel.emit("terminal:connect", Map.of("ticket", ticket, "cols", 80, "rows", 24));
        assertTrue(connected.get(15, TimeUnit.SECONDS), "终端连接应成功");

        panel.emit("terminal:input", Map.of("data", "echo PROXY_OK\r\n"));
        String text = output.get(15, TimeUnit.SECONDS);
        assertNotNull(text, "应收到终端输出");

        panel.emit("terminal:close", Map.of());
        panel.disconnect();
    }

    @Test
    void terminalTicketRejectedWithoutCookie() throws Exception {
        SocketIOClient panel = new SocketIOClient("127.0.0.1", panelPort, "/panel", false,
                java.time.Duration.ofSeconds(5));
        CompletableFuture<Boolean> rejected = new CompletableFuture<>();
        panel.on("auth", (c, e, d) -> rejected.complete(
                d instanceof JsonNode n && n.path("status").asInt() == 401));
        boolean connected = panel.connect();
        if (connected) {
            assertTrue(rejected.get(5, TimeUnit.SECONDS), "无 Cookie 连接应收到 401");
        }
        
        panel.disconnect();
    }
}
