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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class SingleUserModeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    static Path dataDir;

    static int panelPort;
    static PanelServer panelServer;
    static DatabaseManager db;
    static String rootPw;

    static HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void setUp() throws Exception {
        panelPort = TestUtil.freePort();
        DaemonConfig daemonConfig = TestUtil.writeDaemonConfig(dataDir, TestUtil.freePort());
        PanelConfig panelConfig = TestUtil.writePanelConfig(dataDir, panelPort);
        db = new DatabaseManager(panelConfig.dbPath());

        UserService userService = new UserService(new UserRepository(db), new RootAccountRepository(db));
        rootPw = userService.firstLaunchInit();

        
        User user = new User();
        user.setId(IdUtil.uuid());
        user.setUsername("legacy-user");
        user.setPassword(Argon2Util.hash("legacy-pass-123"));
        user.setRole(UserRole.NORMAL);
        user.setCreatedAt(System.currentTimeMillis() / 1000);
        user.setUpdatedAt(System.currentTimeMillis() / 1000);
        new UserRepository(db).insert(user);

        panelServer = new PanelServer(panelConfig, db, daemonConfig, dataDir.toString(), true,
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
        if (db != null) {
            db.close();
        }
    }

    private HttpResponse<String> login(String username, String password) throws Exception {
        String body = MAPPER.writeValueAsString(java.util.Map.of("username", username, "password", password));
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> apiGet(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + panelPort + path)).GET();
        if (token != null) {
            builder.header("X-LingConsole-Token", token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rootLoginWorks() throws Exception {
        HttpResponse<String> resp = login("ling", rootPw);
        assertEquals(200, resp.statusCode());
    }

    @Test
    void legacyUserCannotLoginInSingleUserMode() throws Exception {
        HttpResponse<String> resp = login("legacy-user", "legacy-pass-123");
        assertEquals(401, resp.statusCode());
    }

    @Test
    void userManagementApiDisabled() throws Exception {
        String token = login("ling", rootPw).body();
        String t = MAPPER.readTree(token).path("data").path("token").asText();
        HttpResponse<String> users = apiGet("/api/users", t);
        assertEquals(404, users.statusCode());
        HttpResponse<String> groups = apiGet("/api/permission-groups", t);
        assertEquals(404, groups.statusCode());
    }

    @Test
    void usersPageRedirects() throws Exception {
        
        HttpResponse<String> loginResp = login("ling", rootPw);
        String setCookie = loginResp.headers().firstValue("Set-Cookie").orElse("");
        String cookie = setCookie.split(";")[0];
        HttpResponse<String> resp = http.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + panelPort + "/users"))
                .header("Cookie", cookie)
                .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, resp.statusCode());
        assertTrue(resp.headers().firstValue("Location").orElse("").endsWith("/dashboard"));
    }

    @Test
    void meReportsSingleUserMode() throws Exception {
        String token = login("ling", rootPw).body();
        String t = MAPPER.readTree(token).path("data").path("token").asText();
        JsonNode data = MAPPER.readTree(apiGet("/api/auth/me", t).body()).path("data");
        assertTrue(data.path("singleUserMode").asBoolean());
    }
}
