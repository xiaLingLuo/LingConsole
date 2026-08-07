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
    let groups = [];
    let keys = { all: [], labels: {}, grantable: [] };
    let editingId = null;
    let tags = [];

    const ALL_MSG = "这将一次性授予所有权限！这是高危操作！您确认要这么做吗？！";
    const BATCH_MSG = "这是批量权限授予！您确认要这么做吗？！";

    function load() {
        return Promise.all([
            API.get("/permission-groups").then(function (g) { groups = g || []; }),
            API.get("/permission-keys").then(function (k) { keys = k || { all: [], labels: {}, grantable: [] }; })
        ]).then(function () {
            renderTree();
            renderGroups();
        }).catch(function (e) {
            LC.dialog.alert(e.message);
        });
    }

    // ---------------- 权限树预览 ----------------

    let treeExpanded = new Set();

    function renderTree() {
        const el = document.getElementById("perm-tree");
        const all = keys.all || [];
        const labels = keys.labels || {};
        if (!all.length) {
            el.innerHTML = '<div class="lc-config-note">暂无权限节点</div>';
            return;
        }
        const root = {};
        all.forEach(function (k) {
            const segs = k.split(".");
            let node = root;
            segs.forEach(function (s) {
                if (!node[s]) node[s] = {};
                node = node[s];
            });
        });
        el.innerHTML = '<ul>' + treeNode(root, "", labels) + '</ul>';
        el.querySelectorAll(".lc-pg-node").forEach(function (n) {
            n.addEventListener("click", function () {
                const full = n.getAttribute("data-node");
                showDetail(full);
                if (n.getAttribute("data-leaf") === "1") return;
                const key = n.getAttribute("data-key");
                if (treeExpanded.has(key)) treeExpanded.delete(key); else treeExpanded.add(key);
                renderTree();
            });
        });
    }

    function treeNode(obj, path, labels) {
        return Object.keys(obj).sort().map(function (s) {
            const full = path ? path + "." + s : s;
            const kids = obj[s];
            const leaf = Object.keys(kids).length === 0;
            const isExpanded = treeExpanded.has(full);
            const label = labels[full] ? '<span class="label">' + escapeHtml(labels[full]) + '</span>' : "";
            const children = leaf ? "" : (isExpanded ? '<ul>' + treeNode(kids, full, labels) + '</ul>' : "");
            return '<li>' +
                '<div class="lc-pg-node" data-node="' + escapeAttr(full) + '" data-key="' + escapeAttr(full) + '" data-leaf="' + (leaf ? "1" : "0") + '">' +
                '<span class="caret">' + (leaf ? "" : (isExpanded ? "▾" : "▸")) + '</span>' +
                '<span class="name' + (leaf ? " is-leaf" : "") + '">' + escapeHtml(s) + '</span>' + label +
                '</div>' + children +
                '</li>';
        }).join("");
    }

    function showDetail(node) {
        const input = document.getElementById("perm-detail");
        if (input) input.value = node || "";
    }

    // ---------------- 权限组列表 ----------------

    function renderGroups() {
        const el = document.getElementById("group-list");
        if (!groups.length) {
            el.innerHTML = '<div class="lc-config-note">暂无权限组</div>';
        } else {
            el.innerHTML = groups.map(function (g) {
                return '<div class="lc-pg-group-row" data-edit="' + escapeAttr(g.id) + '">' +
                    '<div><div class="gname">' + escapeHtml(g.name) + '</div>' +
                    '<div class="gmeta">' + (g.groupId ? 'ID: <code class="gid">' + escapeHtml(g.groupId) + '</code> · ' : '') + escapeHtml(g.description || "") + ' · ' + (g.permissions || []).length + ' 项权限</div></div>' +
                    '<span class="gdel" data-del="' + escapeAttr(g.id) + '">✕ 删除</span>' +
                    '</div>';
            }).join("");
        }
        const addBtn = document.createElement("div");
        addBtn.className = "lc-pg-group-row lc-pg-group-add";
        addBtn.textContent = "+ 新建权限组";
        addBtn.addEventListener("click", toggleCreate);
        el.appendChild(addBtn);

        el.querySelectorAll("[data-edit]").forEach(function (row) {
            row.addEventListener("click", function () { openEditor(row.getAttribute("data-edit")); });
        });
        el.querySelectorAll("[data-del]").forEach(function (s) {
            s.addEventListener("click", function (e) {
                e.stopPropagation();
                deleteGroup(s.getAttribute("data-del"));
            });
        });
    }

    function toggleCreate() {
        const editor = document.getElementById("group-editor");
        const creating = editingId === null && editor.style.display !== "none";
        if (creating) {
            closeEditor();
        } else {
            openEditor(null);
        }
    }

    async function deleteGroup(gid) {
        if (!await LC.dialog.confirm("确认删除该权限组? 相关用户将失去该组权限")) return;
        API.del("/permission-groups/" + gid).then(load).catch(function (e) { LC.dialog.alert(e.message); });
    }

    // ---------------- 权限组编辑器 ----------------

    function openEditor(gid) {
        editingId = gid || null;
        const g = gid ? groups.find(function (x) { return x.id === gid; }) : null;
        document.getElementById("pg-groupid").value = g ? (g.groupId || "") : "";
        document.getElementById("pg-name").value = g ? g.name : "";
        document.getElementById("pg-desc").value = g ? (g.description || "") : "";
        tags = g ? (g.permissions || []).slice() : [];
        renderTags();
        document.getElementById("pg-error").textContent = "";
        document.getElementById("group-editor").style.display = "block";
    }

    function closeEditor() {
        document.getElementById("group-editor").style.display = "none";
        editingId = null;
    }

    function renderTags() {
        const box = document.getElementById("pg-tags");
        box.innerHTML = tags.map(function (t) {
            let cls = "lc-perm-tag";
            if (t === "*") cls += " lc-perm-tag--all";
            else if (t.indexOf("*") !== -1) cls += " lc-perm-tag--wild";
            return '<span class="' + cls + '">' + escapeHtml(t) + '<span class="x" data-rm="' + escapeAttr(t) + '">✕</span></span>';
        }).join("") +
            '<div class="lc-perm-addrow">' +
            '<button type="button" class="lc-perm-addbtn" id="btn-add-node" title="将当前输入加入权限节点">＋</button>' +
            '<input class="lc-perm-input" id="pg-input" placeholder="输入后 回车 / 点＋ / 末尾输入 + 或 = 添加, Tab 预览, 支持 * 与自动补全" autocomplete="off">' +
            '</div>';
        box.querySelectorAll("[data-rm]").forEach(function (x) {
            x.addEventListener("click", function () {
                tags = tags.filter(function (t) { return t !== x.getAttribute("data-rm"); });
                renderTags();
                focusInput();
            });
        });
        const input = document.getElementById("pg-input");
        if (input) {
            input.addEventListener("keydown", onInputKeydown);
            input.addEventListener("input", onInputChange);
            input.addEventListener("focus", onInputChange);
            input.addEventListener("blur", function () { setTimeout(function () { hideAc(); }, 150); });
            input.focus();
        }
        const addBtn = document.getElementById("btn-add-node");
        if (addBtn) addBtn.addEventListener("click", function (e) {
            e.preventDefault();
            addCurrentInput();
        });
    }

    function focusInput() {
        const input = document.getElementById("pg-input");
        if (input) input.focus();
    }

    function addTag(node) {
        node = (node || "").trim();
        if (!node) return;
        if (tags.indexOf(node) !== -1) return;
        tags.push(node);
        renderTags();
    }

    // ---------------- 自动补全 ----------------

    function childSegments(prefixSegs) {
        const res = new Set();
        (keys.all || []).forEach(function (k) {
            const segs = k.split(".");
            if (segs.length <= prefixSegs.length) return;
            let match = true;
            for (let i = 0; i < prefixSegs.length; i++) {
                if (prefixSegs[i] === "*") continue;
                if (segs[i] !== prefixSegs[i]) { match = false; break; }
            }
            if (match) res.add(segs[prefixSegs.length]);
        });
        return Array.from(res);
    }

    function suggest(input) {
        input = (input || "").trim();
        if (!input) return [];
        if (input === "*") return [];
        const segs = input.split(".");
        if (input.endsWith(".")) {
            const prefix = segs.slice(0, -1);
            if (prefix.length && prefix[prefix.length - 1] === "*") {
                return childSegments(prefix).map(function (c) { return input + c + "."; });
            }
            const out = [input + "*"];
            childSegments(prefix).forEach(function (c) { out.push(input + c + "."); });
            return out;
        }
        const partial = segs[segs.length - 1];
        const prefix = segs.slice(0, -1);
        const base = prefix.length ? prefix.join(".") + "." : "";
        return childSegments(prefix).filter(function (c) { return c.indexOf(partial) === 0; })
            .map(function (c) { return base + c + "."; });
    }

    function isCompleteNode(v) {
        if (v === "*" || v.endsWith(".*")) return true;
        return (keys.all || []).indexOf(v) !== -1;
    }

    function onSuggestionPick(v) {
        const input = document.getElementById("pg-input");
        if (input) input.value = v;
        if (isCompleteNode(v)) {
            addCurrentInput();
        } else {
            computeSuggestions(v);
            if (input) input.focus();
        }
    }

    function showAc(list) {
        const dd = document.getElementById("pg-ac");
        if (!list.length) {
            dd.innerHTML = '<div class="lc-ac-empty">无补全建议</div>';
            dd.style.display = "block";
            activeAcIndex = 0;
            return;
        }
        dd.innerHTML = list.map(function (item, i) {
            const label = keys.labels[item.slice(0, -1)] ? '<span class="ac-label">' + escapeHtml(keys.labels[item.slice(0, -1)]) + '</span>' : "";
            return '<div class="lc-ac-item" data-v="' + escapeAttr(item) + '" data-i="' + i + '">' + escapeHtml(item) + label + '</div>';
        }).join("");
        dd.style.display = "block";
        dd.querySelectorAll(".lc-ac-item").forEach(function (it) {
            it.addEventListener("mousedown", function (e) {
                e.preventDefault();
                onSuggestionPick(it.getAttribute("data-v"));
            });
        });
        activeAcIndex = 0;
        markAcActive();
    }

    function hideAc() {
        document.getElementById("pg-ac").style.display = "none";
    }

    let activeAcIndex = 0;
    let pendingDeep = false;
    function markAcActive() {
        const items = document.querySelectorAll("#pg-ac .lc-ac-item");
        items.forEach(function (it, i) {
            it.classList.toggle("active", i === activeAcIndex);
        });
    }

    function computeSuggestions(inputValue) {
        pendingDeep = false;
        showAc(suggest(inputValue));
    }

    function onInputChange() {
        const input = document.getElementById("pg-input");
        if (!input) return;
        const v = input.value;
        if (v.endsWith("+") || v.endsWith("=")) {
            input.value = v.slice(0, -1).trim();
            addCurrentInput();
            return;
        }
        computeSuggestions(v);
    }

    function onInputKeydown(e) {
        if (e.key === "ArrowDown") {
            e.preventDefault();
            const items = document.querySelectorAll("#pg-ac .lc-ac-item");
            if (items.length) { activeAcIndex = (activeAcIndex + 1) % items.length; markAcActive(); }
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            const items = document.querySelectorAll("#pg-ac .lc-ac-item");
            if (items.length) { activeAcIndex = (activeAcIndex - 1 + items.length) % items.length; markAcActive(); }
        } else if (e.key === "Tab") {
            e.preventDefault();
            const items = document.querySelectorAll("#pg-ac .lc-ac-item");
            if (!items.length) return;
            const input = document.getElementById("pg-input");
            const currentVal = input ? input.value : "";
            if (pendingDeep) {
                pendingDeep = false;
                computeSuggestions(currentVal);
                const items2 = document.querySelectorAll("#pg-ac .lc-ac-item");
                if (items2.length) {
                    activeAcIndex = 0;
                    markAcActive();
                    if (input) input.value = items2[0].getAttribute("data-v");
                }
                return;
            }
            const previewing = currentVal === items[activeAcIndex].getAttribute("data-v");
            if (previewing) {
                activeAcIndex = (activeAcIndex + 1) % items.length;
            } else {
                activeAcIndex = 0;
            }
            const picked = items[activeAcIndex].getAttribute("data-v");
            markAcActive();
            if (input) input.value = picked;
            pendingDeep = picked.endsWith(".") && !previewing;
        } else if (e.key === "Enter") {
            e.preventDefault();
            addCurrentInput();
        }
    }

    async function addCurrentInput() {
        const input = document.getElementById("pg-input");
        const node = input ? input.value.trim() : "";
        if (!node) return;
        const confirmed = await confirmWildcard(node);
        if (!confirmed) {
            if (input) input.value = "";
            return;
        }
        addTag(node);
        if (input) input.value = "";
        hideAc();
        if (input) input.focus();
    }

    async function confirmWildcard(node) {
        if (node === "*") {
            return await LC.dialog.confirm(ALL_MSG);
        }
        if (node.indexOf("*") !== -1) {
            if (node.endsWith(".*")) {
                const prefix = node.slice(0, -2);
                return await LC.dialog.confirm("这将授予" + prefix + "下的所有权限！您确认要这么做吗？！");
            }
            return await LC.dialog.confirm(BATCH_MSG);
        }
        return true;
    }

    // ---------------- 保存 ----------------

    function save() {
        const name = document.getElementById("pg-name").value.trim();
        const desc = document.getElementById("pg-desc").value.trim();
        const groupId = document.getElementById("pg-groupid").value.trim().toLowerCase();
        if (!name) { document.getElementById("pg-error").textContent = "请输入名称"; return; }
        if (!/^[a-z]+$/.test(groupId)) {
            document.getElementById("pg-error").textContent = "权限组 ID 仅允许小写英文字母 (当前: " + groupId + ")";
            return;
        }
        const body = { groupId: groupId, name: name, description: desc, permissions: tags };
        const req = editingId
            ? API.put("/permission-groups/" + editingId, body)
            : API.post("/permission-groups", body);
        req.then(function () {
            closeEditor();
            load();
        }).catch(function (e) {
            document.getElementById("pg-error").textContent = e.message || "保存失败";
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

    document.getElementById("btn-cancel-pg").addEventListener("click", closeEditor);
    document.getElementById("btn-save-pg").addEventListener("click", save);
    document.getElementById("btn-copy-perm").addEventListener("click", function () {
        const input = document.getElementById("perm-detail");
        if (!input || !input.value) return;
        input.select();
        document.execCommand("copy");
        LC.dialog.toast("已复制: " + input.value, "success");
    });

    load();
})();
