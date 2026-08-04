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
package im.xz.cn.lingconsole.common.socketio;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;


public final class SocketIOResponse {

    private SocketIOResponse() {
    }

    public static Map<String, Object> ok(Object request, Object data) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("uuid", uuidOf(request));
        resp.put("status", 200);
        resp.put("message", "success");
        resp.put("data", data == null ? Map.of() : data);
        return resp;
    }

    public static Map<String, Object> error(Object request, int status, String message) {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("uuid", uuidOf(request));
        resp.put("status", status);
        resp.put("message", message);
        resp.put("data", Map.of());
        return resp;
    }

    public static String uuidOf(Object request) {
        if (request instanceof JsonNode n && n.isObject() && n.has("uuid")) {
            return n.get("uuid").asText();
        }
        return null;
    }

    public static String extract(Object request, String field) {
        if (request instanceof JsonNode n && n.isObject()) {
            JsonNode inner = n.get("data");
            if (inner != null && inner.isObject() && inner.has(field)) {
                return inner.get(field).asText(null);
            }
            if (n.has(field)) {
                return n.get(field).asText(null);
            }
        }
        return null;
    }
}
