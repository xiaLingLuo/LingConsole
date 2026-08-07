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
package im.xz.cn.lingconsole.common.addon;

import im.xz.cn.lingconsole.addon.AddonSocketConnection;
import im.xz.cn.lingconsole.common.socketio.SocketIOConnection;
import im.xz.cn.lingconsole.common.socketio.SocketIOResponse;
import im.xz.cn.lingconsole.common.socketio.SocketIOServer;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;


public final class AddonSocketSupport {

    private AddonSocketSupport() {
    }

    public static void apply(SocketIOServer server, AddonSocketRegistry registry,
                             Predicate<SocketIOConnection> authenticated,
                             BiPredicate<SocketIOConnection, String> permitted) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(authenticated, "authenticated");
        Objects.requireNonNull(permitted, "permitted");
        for (String addonName : registry.addonNames()) {
            apply(server, registry, addonName, authenticated, permitted);
        }
    }

    
    public static void apply(SocketIOServer server, AddonSocketRegistry registry, String addonName,
                             Predicate<SocketIOConnection> authenticated,
                             BiPredicate<SocketIOConnection, String> permitted) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(authenticated, "authenticated");
        Objects.requireNonNull(permitted, "permitted");
        for (AddonSocketRegistry.Registration reg : registry.all(addonName)) {
            register(server, registry, addonName, reg, authenticated, permitted);
        }
    }

    private static void register(SocketIOServer server, AddonSocketRegistry registry, String addonName,
                                 AddonSocketRegistry.Registration reg,
                                  Predicate<SocketIOConnection> authenticated,
                                  BiPredicate<SocketIOConnection, String> permitted) {
        server.on(reg.owner(), reg.namespace(), reg.event(), (conn, event, data) -> {
            AddonSocketRegistry.Registration current = registry.find(addonName, reg.namespace(), reg.event());
            if (current == null) {
                return;
            }
            if (!authenticated.test(conn)) {
                conn.emit(event, SocketIOResponse.error(data, 401, "未认证"));
                return;
            }
            if (!permitted.test(conn, current.requiredPermission())) {
                conn.emit(event, SocketIOResponse.error(data, 403, "无权限"));
                return;
            }
            current.handler().handle(wrap(conn), event, data);
        });
    }

    private static AddonSocketConnection wrap(SocketIOConnection conn) {
        return new AddonSocketConnection() {
            @Override
            public String sessionId() {
                return conn.sessionId();
            }

            @Override
            public void emit(String event, Object data) {
                conn.emit(event, data);
            }

            @Override
            public void close() {
                conn.close();
            }
        };
    }
}
