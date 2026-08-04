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


public class SocketIOConnection {

    private final SocketIOServer server;
    private final SocketIOSession session;
    private final String namespace;

    public SocketIOConnection(SocketIOServer server, SocketIOSession session, String namespace) {
        this.server = server;
        this.session = session;
        this.namespace = namespace;
    }

    public String sessionId() {
        return session.sid();
    }

    public String namespace() {
        return namespace;
    }

    public String cookie(String name) {
        return session.cookie(name);
    }

    
    public void emit(String event, Object data) {
        server.sendTo(session, namespace, event, data);
    }

    
    public void close() {
        session.close();
    }
}
