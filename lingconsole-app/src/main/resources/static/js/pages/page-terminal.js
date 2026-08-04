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
(function () {
    const parts = window.location.pathname.split("/");
    const NODE_ID = parts[2] || "";
    const APP_ID = parts[3] || "";

    let socket = null;
    let term = null;
    let appRunning = false;
    let appName = "应用";
    let statusTimer = null;
    let fitAddon = null;

    function fitTerminal() {
        if (!term) return;
        const parent = term.element && term.element.parentElement;
        if (!parent) return;
        const core = term._core;
        const dims = core && core._renderService && core._renderService.dimensions;
        if (dims && dims.actualCellWidth > 0 && dims.actualCellHeight > 0) {
            const cols = Math.max(2, Math.floor(parent.clientWidth / dims.actualCellWidth));
            const rows = Math.max(2, Math.floor(parent.clientHeight / dims.actualCellHeight));
            if (cols !== term.cols || rows !== term.rows) {
                term.resize(cols, rows);
            }
            return;
        }
        try { fitAddon.fit(); } catch (e) {  }
    }

    function setInputEnabled(enabled) {
        appRunning = enabled;
        term.options.disableStdin = !enabled;
    }

    function stopStatusPoll() {
        if (statusTimer) {
            clearInterval(statusTimer);
            statusTimer = null;
        }
    }

    function startStatusPoll() {
        if (statusTimer || !APP_ID) return;
        statusTimer = setInterval(function () {
            API.get("/nodes/" + NODE_ID + "/apps/" + APP_ID).then(function (app) {
                if (app && app.status > 0) {
                    stopStatusPoll();
                    connectAppTerminal();
                }
            }).catch(function () {  });
        }, 3000);
    }

    async function connectAppTerminal() {
        const meta = document.getElementById("terminal-meta");
        const body = { cols: term.cols || 80, rows: term.rows || 24, appId: APP_ID };
        try {
            const data = await API.post("/nodes/" + NODE_ID + "/terminal/passport", body);
            meta.textContent = appName + " [运行中]";
            setInputEnabled(true);
            connectTerminal(data.ticket, APP_ID);
        } catch (e) {
            setInputEnabled(false);
            startStatusPoll();
        }
    }

    function init() {
        const container = document.getElementById("terminal-container");
        if (!container) return;

        term = new Terminal({
            cursorBlink: true,
            fontSize: 14,
            fontFamily: '"Cascadia Code", Consolas, "Courier New", monospace',
            theme: {
                background: "#1A1A2E",
                foreground: "#E8EAF0",
                cursor: "#66CCFF",
                selectionBackground: "rgba(102, 204, 255, 0.3)"
            }
        });
        fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(container);
        fitTerminal();

        if (document.fonts && document.fonts.ready) {
            document.fonts.ready.then(fitTerminal);
        }

        
        setInterval(fitTerminal, 1000);

        term.onData(function (data) {
            if (!appRunning) return;
            if (socket && socket.connected) {
                socket.emit("terminal:input", { data: data });
            }
        });
        term.onResize(function (size) {
            if (socket && socket.connected) {
                socket.emit("terminal:resize", { cols: size.cols, rows: size.rows });
            }
        });

        document.getElementById("btn-clear-terminal").addEventListener("click", function () {
            term.clear();
        });
        window.addEventListener("resize", function () {
            fitTerminal();
        });

        if (APP_ID) {
            const stopBtn = document.getElementById("btn-stop-app");
            const restartBtn = document.getElementById("btn-restart-app");
            if (stopBtn) {
                stopBtn.style.display = "";
                stopBtn.addEventListener("click", function () { controlApp("stop"); });
            }
            if (restartBtn) {
                restartBtn.style.display = "";
                restartBtn.addEventListener("click", function () { controlApp("restart"); });
            }
        }

        getPassport();
    }

    async function controlApp(action) {
        const label = action === "stop" ? "停止" : "重启";
        try {
            await API.post("/nodes/" + NODE_ID + "/apps/" + APP_ID + "/" + action);
            term.write("\r\n\x1b[36m[LingConsole] 已请求" + label + "应用\x1b[0m\r\n");
        } catch (e) {
            term.write("\r\n\x1b[31m[LingConsole] " + label + "失败: " + e.message + "\x1b[0m\r\n");
        }
    }

    async function getPassport() {
        const meta = document.getElementById("terminal-meta");
        if (APP_ID) {
            appName = "应用 " + APP_ID.slice(0, 8);
            try {
                const app = await API.get("/nodes/" + NODE_ID + "/apps/" + APP_ID);
                if (app && app.name) appName = app.name;
                if (app && app.status > 0) {
                    meta.textContent = appName + " [运行中]";
                    setInputEnabled(true);
                    connectAppTerminal();
                    return;
                }
            } catch (e) {  }
            setInputEnabled(false);
            meta.textContent = appName + " [已停止]";
            try {
                const l = await API.get("/nodes/" + NODE_ID + "/apps/" + APP_ID + "/logs?count=200");
                const logs = (l && l.logs) || [];
                if (logs.length) term.write(logs.join("\r\n") + "\r\n");
            } catch (e) {  }
            term.write("\r\n\x1b[33m[LingConsole] 应用未运行, 等待启动后自动连接...\x1b[0m\r\n");
            startStatusPoll();
            return;
        }
        meta.textContent = "节点终端 (root 登录)";
        setInputEnabled(true);
        try {
            const data = await API.post("/nodes/" + NODE_ID + "/terminal/passport",
                    { cols: term.cols || 80, rows: term.rows || 24 });
            connectTerminal(data.ticket, "");
        } catch (e) {
            setInputEnabled(false);
            term.write("\r\n[LingConsole] 终端不可用: " + e.message + "\r\n");
        }
    }

    function connectTerminal(ticket, appId) {
        if (socket) {
            socket.disconnect();
            socket = null;
        }
        const meta = document.getElementById("terminal-meta");
        term.write("\x1b[36m[LingConsole] 正在连接终端 ...\x1b[0m\r\n");

        socket = io("/panel", {
            transports: ["websocket"],
            reconnection: false
        });

        socket.on("connect", function () {
            socket.emit("terminal:connect", {
                ticket: ticket,
                appId: appId,
                cols: term.cols || 80,
                rows: term.rows || 24
            });
        });

        socket.on("auth", function (data) {
            if (data && data.status !== 200) {
                term.write("\r\n\x1b[31m[LingConsole] 面板认证失败: " + (data.message || "") + "\x1b[0m\r\n");
            }
        });

        socket.on("terminal:connect", function (data) {
            if (data && data.status === 200) {
                term.write("\x1b[36m[LingConsole] 已连接\x1b[0m\r\n\r\n");
                socket.emit("terminal:resize", { cols: term.cols, rows: term.rows });
            } else {
                term.write("\r\n\x1b[31m[LingConsole] 终端连接失败: " + ((data && data.message) || "未知错误") + "\x1b[0m\r\n");
                setInputEnabled(false);
            }
        });

        socket.on("terminal:auth", function (data) {
            if (data && data.status !== 200) {
                term.write("\r\n\x1b[31m[LingConsole] 终端认证失败: " + ((data && data.message) || "") + "\x1b[0m\r\n");
            }
        });

        socket.on("terminal:output", function (data) {
            if (data && data.data) {
                term.write(data.data);
            }
        });

        socket.on("terminal:status", function (data) {
            setInputEnabled(!!(data && data.running));
        });

        socket.on("terminal:exit", function () {
            setInputEnabled(false);
            if (APP_ID) {
                meta.textContent = "应用终端 [已停止]";
                term.write("\r\n\x1b[33m[LingConsole] 应用已停止, 终端已关闭\x1b[0m\r\n");
                startStatusPoll();
            } else {
                term.write("\r\n\x1b[33m[LingConsole] 终端已关闭\x1b[0m\r\n");
            }
        });

        socket.on("disconnect", function (reason) {
            term.write("\r\n\x1b[33m[LingConsole] 连接断开: " + reason + "\x1b[0m\r\n");
        });
    }

    document.addEventListener("DOMContentLoaded", init);

    window.addEventListener("beforeunload", function () {
        if (socket) {
            socket.emit("terminal:close", {});
            socket.disconnect();
        }
    });

    window.closeTerminal = function () {
        if (socket) {
            socket.emit("terminal:close", {});
            socket.disconnect();
        }
        window.close();
        setTimeout(function () {
            if (!window.closed) {
                window.location.href = APP_ID ? "/apps" : "/nodes";
            }
        }, 100);
    };
})();
