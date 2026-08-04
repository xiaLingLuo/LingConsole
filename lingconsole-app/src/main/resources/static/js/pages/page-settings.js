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
    const LABELS = {
        host: "监听地址", port: "端口", sessionTimeout: "会话超时 (秒)",
        maxLoginAttempts: "最大登录尝试", lockoutDuration: "锁定时间 (秒)",
        rateLimitPerSecond: "限流 (次/秒)", theme: "主题", language: "语言",
        dbPath: "数据库路径",
        name: "节点名称", whiteListEnabled: "IP 白名单", whiteListIps: "白名单 IP",
        authTimeout: "认证超时 (秒)", defaultAppPath: "应用目录",
        maxFileTasks: "最大文件任务", maxZipSize: "最大压缩 (MB)",
        outputBufferSize: "输出缓冲 (KB)", softShutdownEnabled: "软关闭",
        softShutdownWaitSeconds: "软关闭等待 (秒)"
    };

    function loadConfig() {
        API.get("/settings").then(function (data) {
            renderConfig("panel-config", data.panel);
            renderConfig("daemon-config", data.daemon);
        }).catch(function (e) {
            console.error("加载配置失败", e);
        });
    }

    function renderConfig(tableId, config) {
        const tbody = document.getElementById(tableId);
        if (!tbody || !config) return;
        tbody.innerHTML = Object.entries(config).map(function (entry) {
            const key = entry[0];
            const value = entry[1];
            return '<tr>' +
                '<td class="lc-config-key">' + (LABELS[key] || key) + '</td>' +
                '<td><code>' + escapeHtml(formatValue(value)) + '</code></td>' +
                '</tr>';
        }).join("");
    }

    function formatValue(v) {
        if (v === null || v === undefined) return "--";
        if (typeof v === "boolean") return v ? "启用" : "禁用";
        if (Array.isArray(v)) return v.join(", ");
        return String(v);
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }


    const form = document.getElementById("password-form");
    const errorEl = document.getElementById("password-error");
    if (form) {
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            const oldPassword = document.getElementById("old-password").value;
            const newPassword = document.getElementById("new-password").value;
            const confirmPassword = document.getElementById("confirm-password").value;
            if (!oldPassword || !newPassword) {
                errorEl.textContent = "请填写完整";
                return;
            }
            if (newPassword !== confirmPassword) {
                errorEl.textContent = "两次输入的新密码不一致";
                return;
            }
            API.put("/auth/password", { oldPassword: oldPassword, newPassword: newPassword })
                .then(function () {
                    LC.dialog.alert("密码修改成功, 请重新登录");
                    window.app.logout();
                })
                .catch(function (err) {
                    errorEl.textContent = err.message || "修改失败";
                });
        });
    }

    loadConfig();
})();
