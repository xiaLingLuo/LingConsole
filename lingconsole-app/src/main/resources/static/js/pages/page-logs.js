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
    const tbody = document.getElementById("logs-tbody");
    const form = document.getElementById("logs-filter");
    const pageLabel = document.getElementById("logs-page");
    let page = 1;
    let totalPages = 1;
    let requestGeneration = 0;

    function load() {
        const generation = ++requestGeneration;
        const params = new URLSearchParams({page: String(page), pageSize: "50", sourceType: value("logs-source") || "CORE"});
        [["q", "logs-q"], ["pluginName", "logs-plugin"], ["userId", "logs-user"],
            ["nodeId", "logs-node"], ["appId", "logs-app"], ["action", "logs-action"],
            ["requestId", "logs-request"]].forEach(function (entry) {
            const current = value(entry[1]);
            if (current) params.set(entry[0], current);
        });
        history.replaceState(null, "", location.pathname + "?" + params.toString());
        API.get("/logs?" + params.toString()).then(function (data) {
            if (generation !== requestGeneration) return;
            totalPages = Math.max(1, data.totalPages || 1);
            page = Math.min(page, totalPages);
            pageLabel.textContent = page + " / " + totalPages;
            document.getElementById("logs-prev").disabled = page <= 1;
            document.getElementById("logs-next").disabled = page >= totalPages;
            render(data.logs || []);
        }).catch(function (e) {
            console.error(e);
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="7" class="lc-table__empty">加载失败: ' + escapeHtml(e.message) + '</td></tr>';
            }
        });
    }

    function render(logs) {
        if (!tbody) return;
        if (!logs || logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="lc-table__empty">暂无操作日志</td></tr>';
            return;
        }
        tbody.innerHTML = logs.map(function (log) {
            return '<tr>' +
                '<td>' + window.app.formatTime(log.createdAt) + '</td>' +
                '<td>' + escapeHtml(log.sourceType === "PLUGIN" ? ("插件: " + (log.pluginName || "--")) : "核心") + '</td>' +
                '<td>' + escapeHtml(log.username || log.userId || log.actorType || "--") + '</td>' +
                '<td>' + escapeHtml(log.action || "--") + '</td>' +
                '<td>' + escapeHtml(log.target || "--") + '</td>' +
                '<td>' + escapeHtml(log.ip || "--") + '</td>' +
                '<td>' + escapeHtml(log.detail || "--") + '</td>' +
                '</tr>';
        }).join("");
    }

    function value(id) {
        const element = document.getElementById(id);
        return element ? element.value.trim() : "";
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    const initial = new URLSearchParams(location.search);
    [["q", "logs-q"], ["sourceType", "logs-source"], ["pluginName", "logs-plugin"],
        ["userId", "logs-user"], ["nodeId", "logs-node"], ["appId", "logs-app"],
        ["action", "logs-action"], ["requestId", "logs-request"]].forEach(function (entry) {
        if (initial.has(entry[0])) document.getElementById(entry[1]).value = initial.get(entry[0]);
    });
    page = Math.max(1, Number(initial.get("page")) || 1);
    form.addEventListener("submit", function (event) { event.preventDefault(); page = 1; load(); });
    document.getElementById("logs-prev").addEventListener("click", function () { if (page > 1) { page--; load(); } });
    document.getElementById("logs-next").addEventListener("click", function () { if (page < totalPages) { page++; load(); } });
    load();
})();
