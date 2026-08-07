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
    const grid = document.getElementById("node-grid");
    const modal = document.getElementById("node-modal");
    let nodesById = {};

    function load() {
        API.get("/nodes").then(function (nodes) {
            nodesById = {};
            nodes.forEach(function (n) { nodesById[n.id] = n; });
            render(nodes);
        }).catch(function (e) {
            console.error(e);
            if (grid) {
                grid.innerHTML = '<div class="lc-card lc-app-empty">加载失败: ' + escapeHtml(e.message) + '</div>';
            }
        });
    }

    function render(nodes) {
        if (!grid) return;
        if (!nodes || nodes.length === 0) {
            grid.innerHTML = '<div class="lc-card lc-app-empty">暂无节点, 点击右上角添加</div>';
            return;
        }
        const currentId = window.app.currentNode();
        grid.innerHTML = nodes.map(renderCard).join("");

        grid.querySelectorAll("button[data-action]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                const action = btn.getAttribute("data-action");
                const id = btn.getAttribute("data-id");
                if (action === "connect") {
                    testConnect(id);
                } else if (action === "delete") {
                    removeNode(id);
                } else if (action === "config") {
                    openConfigModal(id);
                } else if (action === "style") {
                    openStyleModal(id, btn.getAttribute("data-name"), btn.getAttribute("data-style"));
                } else if (action === "save-name") {
                    saveNodeName(id);
                } else if (action === "set-current") {
                    window.app.setCurrentNode(id);
                    window.app.loadNodes().then(function () {
                        window.app.renderSidebarNode();
                        render(window.app.nodes());
                    });
                }
            });
        });


        grid.querySelectorAll("input[data-node-name]").forEach(function (inp) {
            const id = inp.getAttribute("data-id");
            inp.addEventListener("input", function () {
                updateNameSaveBtn(inp, id);
            });
            inp.addEventListener("keydown", function (e) {
                if (e.key === "Enter") {
                    e.preventDefault();
                    saveNodeName(id);
                }
            });
        });
    }

    function renderCard(node) {
        const currentId = window.app.currentNode();
        const online = node.status === 1;
        const dotClass = online ? "is-online" : "is-offline";
        const badge = online
            ? '<span class="lc-badge lc-badge--online"><span class="lc-badge__dot"></span>在线</span>'
            : '<span class="lc-badge lc-badge--offline"><span class="lc-badge__dot"></span>离线</span>';
        const isCurrent = node.id === currentId;
        const currentBadge = isCurrent
            ? ' <span class="lc-badge lc-badge--warn">当前节点</span>' : '';
        const currentBtn = isCurrent
            ? '<button class="lc-btn lc-btn--sm lc-btn--primary" disabled>✓ 当前节点</button> '
            : '<button class="lc-btn lc-btn--sm lc-btn--primary" data-action="set-current" data-id="' + escapeAttr(node.id) + '">设为当前</button> ';
        const terminalBtn = window.app.hasPermission("lingconsole.terminal.node." + node.id)
            ? '<a class="lc-btn lc-btn--sm" href="/terminal/' + escapeAttr(node.id) + '">终端</a> ' : '';
        const fileBtn = window.app.hasPermission("lingconsole.file.node." + node.id)
            ? '<a class="lc-btn lc-btn--sm" href="/files/' + escapeAttr(node.id) + '">文件管理</a> ' : '';
        const writeBtn = window.app.hasPermission("lingconsole.node.write." + node.id)
            ? '<button class="lc-btn lc-btn--sm" data-action="connect" data-id="' + escapeAttr(node.id) + '">测试</button> ' +
              '<button class="lc-btn lc-btn--sm" data-action="config" data-id="' + escapeAttr(node.id) + '">配置</button> ' +
              '<button class="lc-btn lc-btn--sm" data-action="style" data-id="' + escapeAttr(node.id) + '" data-name="' + escapeAttr(node.name) + '" data-style="' + escapeAttr(node.style || "auto") + '">系统偏好</button> ' +
              '<button class="lc-btn lc-btn--sm lc-btn--danger" data-action="delete" data-id="' + escapeAttr(node.id) + '">删除</button>'
            : '';
        return '<div class="lc-card lc-app-card lc-node-card">' +
            '<div class="lc-app-card__header">' +
                '<span class="lc-app-card__dot ' + dotClass + '"></span>' +
                '<input class="lc-input lc-input--inline lc-node-name-input" data-node-name data-id="' + escapeAttr(node.id) + '" value="' + escapeAttr(node.name) + '" title="点击可修改名称">' +
                '<button class="lc-btn lc-btn--sm lc-btn--primary lc-node-save" data-action="save-name" data-id="' + escapeAttr(node.id) + '" style="display:none">保存修改</button>' +
                badge + currentBadge +
            '</div>' +
            '<div class="lc-app-card__meta">' +
                '<div class="lc-app-card__meta-line"><span class="lc-app-card__label">ID</span><code>' + escapeHtml(node.id) + '</code></div>' +
                '<div class="lc-app-card__meta-line"><span class="lc-app-card__label">URL</span><code>' + escapeHtml(node.url) + '</code></div>' +
            '</div>' +
            '<div class="lc-app-card__actions lc-node-card__actions">' +
                '<div class="lc-node-card__row">' + currentBtn + terminalBtn + fileBtn + '</div>' +
                (writeBtn ? '<div class="lc-node-card__row">' + writeBtn + '</div>' : '') +
            '</div>' +
            '</div>';
    }

    function updateNameSaveBtn(inp, id) {
        const node = nodesById[id];
        const btn = document.querySelector('button[data-action="save-name"][data-id="' + id + '"]');
        if (!btn) return;
        const dirty = inp.value.trim() !== "" && inp.value !== (node ? node.name : inp.value);
        btn.style.display = dirty ? "" : "none";
    }

    function saveNodeName(id) {
        const node = nodesById[id];
        if (!node) return;
        const inp = document.querySelector('input[data-node-name][data-id="' + id + '"]');
        const name = inp ? inp.value.trim() : "";
        if (!name) {
            LC.dialog.alert("名称不能为空");
            inp && inp.focus();
            return;
        }
        if (name === node.name) {
            updateNameSaveBtn(inp, id);
            return;
        }
        API.put("/nodes/" + id, { name: name, url: node.url })
            .then(function () { load(); })
            .catch(function (e) { LC.dialog.alert(e.message || "保存失败"); });
    }


    let configNodeId = null;

    function openConfigModal(id) {
        const node = nodesById[id];
        if (!node) return;
        configNodeId = id;
        document.getElementById("config-modal-title").textContent = "节点配置 - " + node.name;
        document.getElementById("config-url").value = node.url || "";

        document.getElementById("config-key").value = "";
        document.getElementById("config-error").textContent = "";
        document.getElementById("config-modal").style.display = "flex";
    }

    function saveConfig() {
        if (!configNodeId) return;
        const node = nodesById[configNodeId];
        if (!node) return;
        const url = document.getElementById("config-url").value.trim();
        const key = document.getElementById("config-key").value.trim();
        if (!url) {
            document.getElementById("config-error").textContent = "请填写 URL";
            return;
        }
        const body = { name: node.name, url: url };
        if (key) {
            body.key = key;
        }
        API.put("/nodes/" + configNodeId, body)
            .then(function () {
                document.getElementById("config-modal").style.display = "none";
                load();
            })
            .catch(function (e) {
                document.getElementById("config-error").textContent = e.message || "保存失败";
            });
    }

    function toHttpUrl(url) {
        if (!url) return "";
        if (url.indexOf("wss://") === 0) return "https://" + url.slice(6);
        if (url.indexOf("ws://") === 0) return "http://" + url.slice(5);
        return url;
    }

    function testConnect(id) {
        const node = nodesById[id];
        const statusEl = document.getElementById("connect-panel-status");
        const iframe = document.getElementById("connect-iframe");
        document.querySelectorAll(".connect-err-note").forEach(function (el) { el.remove(); });
        if (statusEl) {
            statusEl.textContent = "测试中...";
            statusEl.style.color = "#94a3b8";
        }
        if (iframe) {
            iframe.src = node ? toHttpUrl(node.url) + "/" : "about:blank";
        }
        API.get("/nodes/" + id + "/connect").then(function (data) {
            if (statusEl) {
                statusEl.textContent = data.online ? "连接成功" : "连接失败";
                statusEl.style.color = data.online ? "#16a34a" : "#e11d48";
            }
            const title = document.getElementById("connect-modal-title");
            if (title) title.textContent = "节点连接测试 - " + (node ? node.name : id);
            const modal = document.getElementById("connect-modal");
            if (modal) modal.style.display = "flex";
            load();
        }).catch(function (e) {
            if (statusEl) {
                statusEl.textContent = "连接失败";
                statusEl.style.color = "#e11d48";
            }
            const title = document.getElementById("connect-modal-title");
            if (title) title.textContent = "节点连接测试 - " + (node ? node.name : id);
            const modal = document.getElementById("connect-modal");
            if (modal) modal.style.display = "flex";
            if (e && e.message) {
                const note = document.createElement("div");
                note.className = "lc-config-note connect-err-note";
                note.style.marginTop = "8px";
                note.textContent = e.message;
                statusEl.parentNode && statusEl.parentNode.appendChild(note);
            }
            load();
        });
    }

    async function removeNode(id) {
        if (!await LC.dialog.confirm("确认删除该节点?")) return;
        API.del("/nodes/" + id).then(function () {
            load();
        }).catch(function (e) {
            LC.dialog.alert(e.message);
        });
    }

    function openModal() {
        if (modal) {
            modal.style.display = "flex";
            const idInput = document.getElementById("node-id");
            if (idInput) idInput.value = "";
        }
    }

    function closeModal() {
        if (modal) modal.style.display = "none";
    }

    function saveNode() {
        const id = document.getElementById("node-id").value.trim();
        const name = document.getElementById("node-name").value.trim();
        const url = document.getElementById("node-url").value.trim();
        const key = document.getElementById("node-key").value.trim();
        if (!id || !name || !url || !key) {
            LC.dialog.alert("请填写完整信息");
            return;
        }
        if (!/^[a-z0-9]+$/.test(id)) {
            LC.dialog.alert("节点 ID 仅允许小写英文字母和阿拉伯数字");
            return;
        }
        API.post("/nodes", { id: id, name: name, url: url, key: key }).then(function () {
            closeModal();
            load();
        }).catch(function (e) {
            LC.dialog.alert(e.message || "添加失败");
        });
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }

    function escapeAttr(s) {
        return escapeHtml(s).replace(/"/g, "&quot;");
    }


    let styleNodeId = null;
    let stylePending = "auto";

    function openStyleModal(id, name, current) {
        styleNodeId = id;
        document.getElementById("style-modal-title").textContent = "系统偏好 - " + name;
        document.getElementById("style-error").textContent = "";
        var radios = document.querySelectorAll("input[name='node-style']");
        for (var i = 0; i < radios.length; i++) {
            radios[i].checked = radios[i].value === (current || "auto");
        }
        document.getElementById("style-modal").style.display = "flex";
    }

    function saveStyle() {
        if (!styleNodeId) return;
        var selected = null;
        document.querySelectorAll("input[name='node-style']").forEach(function (r) {
            if (r.checked) selected = r.value;
        });
        if (!selected) return;
        stylePending = selected;
        if (selected === "auto") {
            doSaveStyle();
        } else {

            document.getElementById("style-modal").style.display = "none";
            document.getElementById("style-confirm-modal").style.display = "flex";
        }
    }

    function doSaveStyle() {
        API.put("/nodes/" + styleNodeId + "/style", { style: stylePending })
            .then(function () {
                document.getElementById("style-confirm-modal").style.display = "none";
                document.getElementById("style-modal").style.display = "none";
                load();
            })
            .catch(function (e) {
                document.getElementById("style-confirm-modal").style.display = "none";
                document.getElementById("style-error").textContent = e.message || "保存失败";
                document.getElementById("style-modal").style.display = "flex";
            });
    }

    document.getElementById("btn-close-style-modal").addEventListener("click", function () {
        document.getElementById("style-modal").style.display = "none";
    });
    document.getElementById("btn-cancel-style-modal").addEventListener("click", function () {
        document.getElementById("style-modal").style.display = "none";
    });
    document.getElementById("btn-save-style").addEventListener("click", saveStyle);
    document.getElementById("btn-close-style-confirm").addEventListener("click", function () {
        document.getElementById("style-confirm-modal").style.display = "none";
    });
    document.getElementById("btn-cancel-style-confirm").addEventListener("click", function () {
        document.getElementById("style-confirm-modal").style.display = "none";
        document.getElementById("style-modal").style.display = "flex";
    });
    document.getElementById("btn-confirm-style").addEventListener("click", doSaveStyle);

    const addBtn = document.getElementById("btn-add-node");
    if (addBtn) {
        addBtn.style.display = window.app.hasPermission("lingconsole.node.write.*") ? "" : "none";
        addBtn.addEventListener("click", openModal);
    }
    const closeBtn = document.getElementById("btn-close-modal");
    if (closeBtn) closeBtn.addEventListener("click", closeModal);
    const cancelBtn = document.getElementById("btn-cancel-modal");
    if (cancelBtn) cancelBtn.addEventListener("click", closeModal);
    const saveBtn = document.getElementById("btn-save-node");
    if (saveBtn) saveBtn.addEventListener("click", saveNode);


    document.getElementById("btn-close-config-modal").addEventListener("click", function () {
        document.getElementById("config-modal").style.display = "none";
    });
    document.getElementById("btn-cancel-config-modal").addEventListener("click", function () {
        document.getElementById("config-modal").style.display = "none";
    });
    document.getElementById("btn-save-config").addEventListener("click", saveConfig);

    const closeConnect = function () {
        document.getElementById("connect-modal").style.display = "none";
    };
    document.getElementById("btn-close-connect-modal").addEventListener("click", closeConnect);
    document.getElementById("btn-close-connect-modal-2").addEventListener("click", closeConnect);


    window.app.onReady(load);
})();
