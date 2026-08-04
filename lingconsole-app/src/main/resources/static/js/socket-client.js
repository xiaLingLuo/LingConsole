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
window.PanelSocket = (function () {
    let socket = null;

    
    function connect(onReady) {
        if (socket) {
            socket.close();
        }
        const url = window.location.origin;
        socket = io(url + "/panel", {
            transports: ["websocket"]
        });

        socket.on("connect", function () {
            console.log("[PanelSocket] websocket connected");
        });

        socket.on("auth", function (data) {
            if (data && data.status === 200) {
                console.log("[PanelSocket] authenticated, sid=" + (data.data && data.data.sid));
                if (onReady) onReady(socket);
            } else {
                console.warn("[PanelSocket] auth failed", data);
            }
        });

        socket.on("connect_error", function (err) {
            console.warn("[PanelSocket] connect error", err);
        });

        socket.on("disconnect", function (reason) {
            console.warn("[PanelSocket] disconnected: " + reason);
        });

        return socket;
    }

    function getSocket() {
        return socket;
    }

    return {
        connect: connect,
        getSocket: getSocket
    };
})();
