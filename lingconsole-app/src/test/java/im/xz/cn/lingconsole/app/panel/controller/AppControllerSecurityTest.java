package im.xz.cn.lingconsole.app.panel.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import im.xz.cn.lingconsole.app.panel.model.AuthUser;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import im.xz.cn.lingconsole.common.model.UserRole;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppControllerSecurityTest {

    @Test
    void readOnlyAppResponseRemovesEveryAdvancedField() {
        User user = new User();
        user.setRole(UserRole.NORMAL);
        AuthUser auth = new AuthUser(user, Set.of("lingconsole.app.read.app-1"));
        ObjectNode app = ApiResponse.mapper().createObjectNode();
        app.put("id", "app-1");
        app.put("name", "safe-name");
        app.put("type", "general");
        app.put("command", "server --token secret");
        app.putArray("args").add("--password=secret");
        app.putObject("environment").put("TOKEN", "secret");
        app.put("workDir", "private");
        app.put("runAsUser", "svc");
        app.put("encoding", "UTF-8");
        app.put("ptyType", "xterm");
        app.put("protectAppFilesFromSymlinkEscape", true);

        var sanitized = AppController.sanitizeAppInfo(auth, app);

        assertEquals("safe-name", sanitized.path("name").asText());
        assertTrue(sanitized.has("type"));
        for (String field : Set.of("command", "args", "environment", "workDir", "runAsUser",
                "encoding", "ptyType", "protectAppFilesFromSymlinkEscape")) {
            assertFalse(sanitized.has(field), field);
        }
    }
}
