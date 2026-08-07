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
    const urlNodeId = window.location.pathname.split("/")[2] || "";
    let NODE_ID = "";
    let apps = [];

    const STATUS_TEXT = { 0: "已停止", 1: "停止中", 2: "启动中", 3: "运行中" };

    function load() {
        API.get("/nodes/" + NODE_ID + "/apps").then(function (list) {
            apps = list || [];
            render();
        }).catch(function (e) {
            renderError(e.message);
        });
    }

    function render() {
        const grid = document.getElementById("app-grid");
        if (!grid) return;
        if (!apps || apps.length === 0) {
            grid.innerHTML = window.app.hasPermission("lingconsole.app.read.*")
                ? '<div class="lc-card lc-app-empty">暂无应用, 点击右上角"新建应用"创建</div>'
                : '<div class="lc-card lc-app-empty">您没有被授予可查看的应用权限</div>';
            return;
        }
        grid.innerHTML = apps.map(renderCard).join("");

        grid.querySelectorAll("button[data-action]").forEach(function (btn) {
            btn.addEventListener("click", function (e) {
                e.stopPropagation();
                const action = btn.getAttribute("data-action");
                const id = btn.getAttribute("data-id");
                if (action === "start") control(id, "start");
                else if (action === "stop") control(id, "stop");
                else if (action === "restart") control(id, "restart");
                else if (action === "delete") removeApp(id);
                else if (action === "advanced") openAdvanced(id);
            });
        });
        grid.querySelectorAll(".lc-app-card__log").forEach(function (el) {
            el.scrollTop = el.scrollHeight;
        });
    }

    function renderCard(app) {
        const status = app.status;
        const dotClass = status === 3 ? "is-running"
            : (status === 2 || status === 1) ? "is-starting" : "is-stopped";
        const badge = status === 3
            ? '<span class="lc-badge lc-badge--online"><span class="lc-badge__dot"></span>' + STATUS_TEXT[status] + '</span>'
            : (status === 2 || status === 1)
                ? '<span class="lc-badge lc-badge--warn"><span class="lc-badge__dot"></span>' + STATUS_TEXT[status] + '</span>'
                : '<span class="lc-badge lc-badge--offline"><span class="lc-badge__dot"></span>已停止</span>';

        const pid = app.pid != null ? "PID " + app.pid : "--";
        const uptime = app.startedAt ? "运行 " + uptimeText(app.startedAt) : "";
        const logs = (app.recentLog || []).map(escapeHtml).join("\n");

        const primaryBtn = status === 3
            ? '<button class="lc-btn lc-btn--sm" data-action="stop" data-id="' + app.id + '">停止</button>'
            : '<button class="lc-btn lc-btn--sm lc-btn--primary" data-action="start" data-id="' + app.id + '">启动</button>';
        const canWrite = window.app.hasPermission("lingconsole.app.write." + app.id);
        const advancedBtn = window.app.hasPermission("lingconsole.app.advanced." + app.id)
        ? '<button class="lc-btn lc-btn--sm" data-action="advanced" data-id="' + app.id + '">高级</button> '
            : '';
        const terminalBtn = window.app.hasPermission("lingconsole.terminal.app." + app.id)
        ? '<a class="lc-btn lc-btn--sm" href="/terminal/' + NODE_ID + '/' + app.id + '">终端</a> ' : '';
        const fileBtn = window.app.hasPermission("lingconsole.file.app." + app.id)
            ? '<a class="lc-btn lc-btn--sm" href="/files/app/' + NODE_ID + '/' + app.id + '">文件</a>' : '';

        return '<div class="lc-card lc-app-card">' +
            '<div class="lc-app-card__header">' +
                '<span class="lc-app-card__dot ' + dotClass + '"></span>' +
                '<span class="lc-app-card__name">' + escapeHtml(app.name) + '</span>' +
                badge +
            '</div>' +
            '<div class="lc-app-card__meta">' +
                '<div class="lc-app-card__meta-line"><span class="lc-app-card__label">ID</span><code>' + escapeHtml(app.id) + '</code></div>' +
                '<div class="lc-app-card__meta-line"><span class="lc-app-card__label">命令</span><code>' + escapeHtml(app.command || "--") + '</code></div>' +
                '<div class="lc-app-card__meta-line"><span class="lc-app-card__label">PID</span>' + pid + ' <span class="lc-app-card__label">' + uptime + '</span></div>' +
            '</div>' +
            '<div class="lc-app-card__log"><pre>' + (logs || "[LingConsole] 暂无输出") + '</pre></div>' +
            '<div class="lc-app-card__actions">' +
                (canWrite ? primaryBtn + '<button class="lc-btn lc-btn--sm" data-action="restart" data-id="' + app.id + '">重启</button>' : '') +
                advancedBtn + terminalBtn + fileBtn +
                (canWrite ? '<button class="lc-btn lc-btn--sm lc-btn--danger" data-action="delete" data-id="' + app.id + '">删除</button>' : '') +
            '</div>' +
            '</div>';
    }

    function renderError(msg) {
        const grid = document.getElementById("app-grid");
        if (grid) {
            grid.innerHTML = '<div class="lc-card lc-app-empty">加载失败: ' + escapeHtml(msg) + '</div>';
        }
    }

    function uptimeText(startedAt) {
        const sec = Math.floor(Date.now() / 1000) - startedAt;
        if (sec < 60) return sec + " 秒";
        if (sec < 3600) return Math.floor(sec / 60) + " 分钟";
        if (sec < 86400) return Math.floor(sec / 3600) + " 小时";
        return Math.floor(sec / 86400) + " 天";
    }

    function control(id, action) {
        API.post("/nodes/" + NODE_ID + "/apps/" + id + "/" + action)
            .then(function () { load(); })
            .catch(function (e) { LC.dialog.alert(e.message || "操作失败"); });
    }

    async function removeApp(id) {
        if (!await LC.dialog.confirm("确认删除应用? 将同时删除其数据目录")) return;
        API.del("/nodes/" + NODE_ID + "/apps/" + id)
            .then(function () { load(); })
            .catch(function (e) { LC.dialog.alert(e.message || "删除失败"); });
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }


    function openModal() {
        document.getElementById("app-modal").style.display = "flex";
        document.getElementById("app-id").value = "";
        document.getElementById("app-error").textContent = "";
        const hint = document.getElementById("app-type-hint");
        if (hint) hint.style.display = document.getElementById("app-type").value === "general" ? "" : "none";
    }
    function closeModal() {
        document.getElementById("app-modal").style.display = "none";
    }
    function saveApp() {
        const id = document.getElementById("app-id").value.trim();
        const name = document.getElementById("app-name").value.trim();
        const command = document.getElementById("app-command").value.trim();
        if (!id || !name || !command) {
            document.getElementById("app-error").textContent = "请填写 ID、名称和启动命令";
            return;
        }
        if (!/^[a-z0-9]+$/.test(id)) {
            document.getElementById("app-error").textContent = "应用 ID 仅允许小写英文字母和阿拉伯数字";
            return;
        }
        const type = document.getElementById("app-type").value;
        if (type === "docker") {
            LC.dialog.alert("Docker 容器支持正在开发中");
            document.getElementById("app-type").value = "general";
            return;
        }
        API.post("/nodes/" + NODE_ID + "/apps", {
            id: id,
            name: name,
            command: command,
            type: type,
            autoStart: document.getElementById("app-autostart").checked,
            autoRestart: document.getElementById("app-autorestart").checked
        }).then(function () {
            closeModal();
            document.getElementById("app-id").value = "";
            document.getElementById("app-name").value = "";
            document.getElementById("app-command").value = "";
            load();
        }).catch(function (e) {
            document.getElementById("app-error").textContent = e.message || "创建失败";
        });
    }


    let advancedAppId = null;
    let advancedProtectionInitiallyEnabled = true;
    let advancedProtectionDisableConfirmed = false;

    function openAdvanced(appId) {
        advancedAppId = appId;
        document.getElementById("adv-error").textContent = "";
        document.getElementById("adv-modal").style.display = "flex";
        API.get("/nodes/" + NODE_ID + "/apps/" + appId + "/advanced").then(function (cfg) {
            document.getElementById("adv-modal-title").textContent = "高级配置: " + (cfg.name || appId);
            document.getElementById("adv-name").value = cfg.name || "";
            document.getElementById("adv-command").value = cfg.command || "";
            document.getElementById("adv-type").value = cfg.type || "general";
            document.getElementById("adv-autostart").checked = !!cfg.autoStart;
            document.getElementById("adv-autorestart").checked = !!cfg.autoRestart;
            document.getElementById("adv-maxrestart").value = cfg.maxRestartCount || 3;
            document.getElementById("adv-workdir").value = cfg.workDir || "";
            advancedProtectionInitiallyEnabled = cfg.protectAppFilesFromSymlinkEscape !== false;
            advancedProtectionDisableConfirmed = false;
            document.getElementById("adv-protect-app-files").checked = advancedProtectionInitiallyEnabled;
            document.getElementById("adv-runas").value = cfg.runAsUser || "";
            document.getElementById("adv-encoding").value = cfg.encoding || "UTF-8";
            document.getElementById("adv-pty").value = cfg.ptyType || "xterm-256color";
            document.getElementById("adv-args").value = (cfg.args || []).join(", ");
            document.getElementById("adv-env").value = Object.entries(cfg.environment || {})
                .map(function (e) { return e[0] + "=" + e[1]; }).join("\n");

            const running = (cfg.status !== 0);
            const locked = ["adv-command", "adv-type", "adv-maxrestart", "adv-workdir",
                "adv-runas", "adv-encoding", "adv-pty", "adv-args", "adv-env"];
            locked.forEach(function (id) {
                document.getElementById(id).disabled = running;
            });
            const note = document.getElementById("adv-note");
            if (note) {
                note.textContent = running
                    ? "应用运行中, 仅名称/自动启动/自动重启可修改"
                    : "应用停止状态下可修改全部配置";
            }
        }).catch(function (e) {
            document.getElementById("adv-error").textContent = e.message || "加载失败";
        });
    }

    async function confirmProtectionDisable() {
        if (!advancedProtectionInitiallyEnabled || advancedProtectionDisableConfirmed) return true;
        const confirmed = await LC.dialog.confirm("关闭后, 应用文件管理将允许跟随符号链接或 Junction 访问工作目录外的文件。确认关闭保护?");
        advancedProtectionDisableConfirmed = confirmed;
        return confirmed;
    }

    async function saveAdvanced() {
        if (!advancedAppId) return;
        const protectAppFiles = document.getElementById("adv-protect-app-files").checked;
        if (!protectAppFiles && !await confirmProtectionDisable()) {
            document.getElementById("adv-protect-app-files").checked = true;
            return;
        }
        const args = document.getElementById("adv-args").value.split(",")
            .map(function (s) { return s.trim(); }).filter(function (s) { return s; });
        const env = {};
        document.getElementById("adv-env").value.split("\n").forEach(function (line) {
            line = line.trim();
            if (!line) return;
            const idx = line.indexOf("=");
            if (idx > 0) env[line.substring(0, idx).trim()] = line.substring(idx + 1).trim();
        });
        API.put("/nodes/" + NODE_ID + "/apps/" + advancedAppId + "/advanced", {
            name: document.getElementById("adv-name").value.trim(),
            command: document.getElementById("adv-command").value.trim(),
            type: document.getElementById("adv-type").value,
            autoStart: document.getElementById("adv-autostart").checked,
            autoRestart: document.getElementById("adv-autorestart").checked,
            maxRestartCount: parseInt(document.getElementById("adv-maxrestart").value, 10) || 0,
            workDir: document.getElementById("adv-workdir").value.trim(),
            runAsUser: document.getElementById("adv-runas").value.trim(),
            encoding: document.getElementById("adv-encoding").value.trim(),
            ptyType: document.getElementById("adv-pty").value.trim(),
            args: args,
            environment: env,
            protectAppFilesFromSymlinkEscape: protectAppFiles,
            confirmDisableAppFileSymlinkProtection: !protectAppFiles && advancedProtectionDisableConfirmed
        }).then(function () {
            document.getElementById("adv-modal").style.display = "none";
            load();
        }).catch(function (e) {
            document.getElementById("adv-error").textContent = e.message || "保存失败";
        });
    }


    function init() {
        const selector = document.getElementById("node-selector");
        const noNode = document.getElementById("no-node");
        const grid = document.getElementById("app-grid");
        const addBtn = document.getElementById("btn-add-app");

        window.app.loadNodes().then(function (nodes) {
            NODE_ID = urlNodeId || window.app.currentNode();
            if (urlNodeId) {
                window.app.setCurrentNode(urlNodeId);
            }
            if (addBtn) {
                addBtn.style.display = window.app.hasPermission("lingconsole.app.write.*") ? "" : "none";
            }


            if (selector) {
                selector.innerHTML = nodes.map(function (n) {
                    const selected = n.id === NODE_ID ? " selected" : "";
                    return '<option value="' + n.id + '"' + selected + '>' + escapeHtml(n.name) + '</option>';
                }).join("");
                if (nodes.length === 0) {
                    selector.innerHTML = '<option value="">无节点</option>';
                }
                selector.addEventListener("change", function () {
                    if (selector.value) {
                        window.app.setCurrentNode(selector.value);
                        window.app.renderSidebarNode();
                        window.location.href = "/apps";
                    }
                });
            }


            if (!NODE_ID || !nodes.some(function (n) { return n.id === NODE_ID; })) {
                if (noNode) noNode.style.display = "flex";
                if (grid) grid.style.display = "none";
                if (addBtn) addBtn.style.display = "none";
                if (selector) selector.disabled = true;
                return;
            }
            load();
        }).catch(function () {  });
    }

    document.getElementById("btn-add-app").addEventListener("click", openModal);
    document.getElementById("btn-close-app-modal").addEventListener("click", closeModal);
    document.getElementById("btn-cancel-app-modal").addEventListener("click", closeModal);
    document.getElementById("btn-save-app").addEventListener("click", saveApp);
    document.getElementById("btn-close-adv-modal").addEventListener("click", function () {
        document.getElementById("adv-modal").style.display = "none";
    });
    document.getElementById("btn-cancel-adv-modal").addEventListener("click", function () {
        document.getElementById("adv-modal").style.display = "none";
    });
    document.getElementById("btn-save-adv").addEventListener("click", saveAdvanced);
    document.getElementById("adv-protect-app-files").addEventListener("change", async function (event) {
        if (!event.target.checked && !await confirmProtectionDisable()) {
            event.target.checked = true;
        }
    });

    const appTypeSel = document.getElementById("app-type");
    if (appTypeSel) {
        appTypeSel.addEventListener("change", function () {
            const hint = document.getElementById("app-type-hint");
            if (hint) hint.style.display = appTypeSel.value === "general" ? "" : "none";
        });
    }


    window.app.onReady(init);
})();
