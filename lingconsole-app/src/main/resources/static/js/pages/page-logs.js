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

    function load() {
        API.get("/logs?page=1&pageSize=50").then(function (data) {
            render(data.logs || []);
        }).catch(function (e) {
            console.error(e);
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="6" class="lc-table__empty">加载失败: ' + escapeHtml(e.message) + '</td></tr>';
            }
        });
    }

    function render(logs) {
        if (!tbody) return;
        if (!logs || logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="lc-table__empty">暂无操作日志</td></tr>';
            return;
        }
        tbody.innerHTML = logs.map(function (log) {
            return '<tr>' +
                '<td>' + window.app.formatTime(log.createdAt) + '</td>' +
                '<td>' + escapeHtml(log.userId || "--") + '</td>' +
                '<td>' + escapeHtml(log.action || "--") + '</td>' +
                '<td>' + escapeHtml(log.target || "--") + '</td>' +
                '<td>' + escapeHtml(log.ip || "--") + '</td>' +
                '<td>' + escapeHtml(log.detail || "--") + '</td>' +
                '</tr>';
        }).join("");
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    load();
})();
