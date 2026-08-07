/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.common.socketio;

public record SocketIOConnectResult(boolean accepted, String message) {

    public static SocketIOConnectResult accept() {
        return new SocketIOConnectResult(true, null);
    }

    public static SocketIOConnectResult reject(String message) {
        return new SocketIOConnectResult(false,
                message == null || message.isBlank() ? "Connection rejected" : message);
    }
}
