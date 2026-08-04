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
package im.xz.cn.lingconsole.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import im.xz.cn.lingconsole.common.socketio.SocketIOResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SocketIOResponseTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void okEchoesUuid() {
        Object request = MAPPER.valueToTree(Map.of("uuid", "req-123", "data", Map.of("x", 1)));
        Map<String, Object> resp = SocketIOResponse.ok(request, Map.of("y", 2));
        assertEquals("req-123", resp.get("uuid"));
        assertEquals(200, resp.get("status"));
    }

    @Test
    void errorCarriesStatusAndMessage() {
        Object request = MAPPER.valueToTree(Map.of("uuid", "req-9"));
        Map<String, Object> resp = SocketIOResponse.error(request, 401, "未认证");
        assertEquals("req-9", resp.get("uuid"));
        assertEquals(401, resp.get("status"));
        assertEquals("未认证", resp.get("message"));
    }

    @Test
    void uuidOfNullRequest() {
        assertNull(SocketIOResponse.uuidOf(null));
    }

    @Test
    void extractNestedDataField() {
        Object request = MAPPER.valueToTree(Map.of("data", Map.of("key", "secret")));
        assertEquals("secret", SocketIOResponse.extract(request, "key"));
    }

    @Test
    void extractTopLevelFieldFallback() {
        Object request = MAPPER.valueToTree(Map.of("id", "app-1"));
        assertEquals("app-1", SocketIOResponse.extract(request, "id"));
    }

    @Test
    void extractFromNonJsonNode() {
        assertNull(SocketIOResponse.extract("not-json", "key"));
    }
}
