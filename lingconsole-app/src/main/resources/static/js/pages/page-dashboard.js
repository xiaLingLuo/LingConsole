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
    let memChart = null;
    const memHistory = { time: [], used: [], free: [] };

    function initChart() {
        const el = document.getElementById("memory-chart");
        if (!el || typeof echarts === "undefined") return;
        memChart = echarts.init(el);
        memChart.setOption({
            tooltip: { trigger: "axis" },
            grid: { left: 50, right: 20, top: 20, bottom: 30 },
            xAxis: {
                type: "category",
                boundaryGap: false,
                data: memHistory.time,
                axisLine: { lineStyle: { color: "#8E8EA0" } },
                axisLabel: { fontSize: 11, color: "#8E8EA0" }
            },
            yAxis: {
                type: "value",
                axisLabel: { fontSize: 11, color: "#8E8EA0" }
            },
            series: [
                {
                    name: "已用内存",
                    type: "line",
                    smooth: true,
                    showSymbol: false,
                    lineStyle: { color: "#66CCFF", width: 2 },
                    areaStyle: { color: "rgba(102, 204, 255, 0.15)" },
                    data: memHistory.used
                },
                {
                    name: "总内存",
                    type: "line",
                    smooth: true,
                    showSymbol: false,
                    lineStyle: { color: "#8E8EA0", width: 1, type: "dashed" },
                    data: memHistory.free
                }
            ]
        });
    }

    function pushMemory(info) {
        if (!info || !info.totalMemory) return;
        const now = new Date();
        const t = String(now.getHours()).padStart(2, "0") + ":" + String(now.getMinutes()).padStart(2, "0") + ":" + String(now.getSeconds()).padStart(2, "0");
        memHistory.time.push(t);
        memHistory.used.push((info.usedMemory / 1024 / 1024).toFixed(1));
        memHistory.free.push((info.totalMemory / 1024 / 1024).toFixed(1));
        if (memHistory.time.length > 60) {
            memHistory.time.shift();
            memHistory.used.shift();
            memHistory.free.shift();
        }
        if (memChart) {
            memChart.setOption({
                xAxis: { data: memHistory.time },
                series: [{ data: memHistory.used }, { data: memHistory.free }]
            });
        }
    }

    function renderStats(data) {
        const info = data.systemInfo;
        const statsGrid = document.querySelector(".lc-grid--4");
        if (!info) {

            if (statsGrid) statsGrid.style.display = "none";
            var note = document.getElementById("no-system-status");
            if (!note) {
                note = document.createElement("div");
                note.id = "no-system-status";
                note.className = "lc-config-note";
                note.textContent = "无查看 LingConsole 运行状态的权限 (system.status)";
                if (statsGrid && statsGrid.parentNode) statsGrid.parentNode.insertBefore(note, statsGrid);
            }
            return;
        }
        if (statsGrid) statsGrid.style.display = "";
        var note = document.getElementById("no-system-status");
        if (note) note.remove();
        setText("stat-nodes", data.nodeCount != null ? data.nodeCount : "--");
        setText("stat-online", data.nodeOnline != null ? data.nodeOnline : "--");
        setText("stat-cpu-cores", info.cpuCores != null ? info.cpuCores : "--");
        setText("stat-cpu-usage", info.cpuUsage != null && info.cpuUsage >= 0 ? "使用率 " + info.cpuUsage.toFixed(1) + "%" : "--");
        setText("stat-mem", window.app.formatSize(info.totalMemory));
        setText("stat-mem-free", "可用 " + window.app.formatSize(info.freeMemory));
        setText("stat-os", info.osName ? info.osName + " " + info.osArch : "--");
        setText("stat-uptime", "运行 " + info.jvmUptime);
        pushMemory(info);
    }

    function renderNodes(nodes) {
        const tbody = document.getElementById("nodes-tbody");
        if (!tbody) return;
        if (!nodes || nodes.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="lc-table__empty">暂无节点</td></tr>';
            return;
        }
        tbody.innerHTML = nodes.map(function (node) {
            const online = node.status === 1;
            const badge = online
                ? '<span class="lc-badge lc-badge--online"><span class="lc-badge__dot"></span>在线</span>'
                : '<span class="lc-badge lc-badge--offline"><span class="lc-badge__dot"></span>离线</span>';
            return '<tr>' +
                '<td>' + escapeHtml(node.name) + '</td>' +
                '<td><code>' + escapeHtml(node.url) + '</code></td>' +
                '<td>' + badge + '</td>' +
                '</tr>';
        }).join("");
    }

    function setText(id, text) {
        const el = document.getElementById(id);
        if (el) el.textContent = text;
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    function renderUserLogs(logs) {
        const tbody = document.getElementById("user-logs-tbody");
        if (!tbody) return;
        if (!logs || logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" class="lc-table__empty">暂无操作记录</td></tr>';
            return;
        }
        tbody.innerHTML = logs.map(function (log) {
            return '<tr>' +
                '<td>' + window.app.formatTime(log.createdAt) + '</td>' +
                '<td>' + escapeHtml(log.action || "--") + '</td>' +
                '<td>' + escapeHtml(log.target || "--") + '</td>' +
                '<td>' + escapeHtml(log.detail || "--") + '</td>' +
                '</tr>';
        }).join("");
    }

    function applyMode(data) {
        const admin = document.getElementById("admin-dashboard");
        const userDash = document.getElementById("user-dashboard");
        const noneHint = document.getElementById("no-permission-hint");
        const mode = data && data.mode;
        if (admin) admin.style.display = (mode === "admin") ? "" : "none";
        if (userDash) userDash.style.display = (mode === "user") ? "" : "none";
        if (noneHint) noneHint.style.display = (mode === "none") ? "" : "none";
        if (mode === "user") {
            renderUserLogs(data.logs || []);
        }
        if (mode === "admin") {
            renderStats(data);
        }
    }

    function loadStats() {
        return API.get("/dashboard/stats").then(function (data) {
            applyMode(data);
            return data;
        }).catch(function () {
            return null;
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        initChart();
        setInterval(loadStats, 5000);
    });

    loadStats().then(function (data) {
        if (data && data.mode === "admin") {
            renderNodes(data.nodesStatus || []);
        }
    });

    API.get("/dashboard/nodes-status").then(function (nodes) {
        renderNodes(nodes);
    }).catch(function () {  });

    window.PanelSocket.connect(function (socket) {
        socket.emit("dashboard:stats");
        socket.on("dashboard:stats", function (data) {
            if (data && data.status === 200 && data.data) {
                applyMode(data.data);
            }
        });
    });
})();
