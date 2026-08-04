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
    let users = [];
    let groups = [];
    let permKeys = {};
    let editingUserId = null;

    const canAssign = function () { return window.app.hasPermission("lingconsole.permission.assign"); };





    function loadUsers() {
        API.get("/users").then(function (list) {
            users = list || [];
            renderUsers();
        }).catch(function (e) {
            document.getElementById("user-tbody").innerHTML =
                '<tr><td colspan="4" class="lc-table__empty">加载失败: ' + escapeHtml(e.message) + '</td></tr>';
        });
    }

    function renderUsers() {
        const tbody = document.getElementById("user-tbody");
        if (!users.length) {
            tbody.innerHTML = '<tr><td colspan="4" class="lc-table__empty">暂无用户</td></tr>';
            return;
        }
        tbody.innerHTML = users.map(function (u) {
            const isRootRow = u.role === 0;
            const roleBadge = u.role === 0
                ? '<span class="lc-badge lc-badge--warn">根</span>'
                : ((u.groups && u.groups.length)
                    ? u.groups.map(function (g) { return '<span class="lc-badge">' + escapeHtml(g) + '</span>'; }).join(" ")
                    : '<span class="lc-badge lc-badge--offline">未分配权限组</span>');
            let permBtn = '';
            if (canAssign()) {
                permBtn = isRootRow
                    ? '<button class="lc-btn lc-btn--sm" data-action="root-block" data-msg="根账户不可变更">权限</button> '
                    : '<button class="lc-btn lc-btn--sm" data-action="perms" data-id="' + u.id + '" data-name="' + escapeAttr(u.username) + '">权限</button> ';
            }
            let editBtn = '';
            if (isRootRow) {
                editBtn = '<button class="lc-btn lc-btn--sm" data-action="root-block" data-msg="请转到系统设置中修改根的密码！">编辑</button> ' +
                    '<button class="lc-btn lc-btn--sm lc-btn--danger" data-action="root-block" data-msg="根账户不可变更">删除</button>';
            } else {
                editBtn = '<button class="lc-btn lc-btn--sm" data-action="edit" data-id="' + u.id + '" data-name="' + escapeAttr(u.username) + '">编辑</button> ' +
                    '<button class="lc-btn lc-btn--sm lc-btn--danger" data-action="delete" data-id="' + u.id + '" data-name="' + escapeAttr(u.username) + '">删除</button>';
            }
            return '<tr>' +
                '<td>' + escapeHtml(u.username) + '</td>' +
                '<td>' + roleBadge + '</td>' +
                '<td>' + window.app.formatTime(u.createdAt) + '</td>' +
                '<td>' + permBtn + editBtn + '</td>' +
                '</tr>';
        }).join("");

        tbody.querySelectorAll("button[data-action]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                const action = btn.getAttribute("data-action");
                if (action === "root-block") {
                    LC.dialog.alert(btn.getAttribute("data-msg") || "根账户不可变更");
                    return;
                }
                const id = btn.getAttribute("data-id");
                const name = btn.getAttribute("data-name");
                if (action === "perms") openPermModal(id, name);
                else if (action === "edit") openEditModal(id, name);
                else if (action === "delete") deleteUser(id, name);
            });
        });
    }





    function openCreateModal() {
        editingUserId = null;
        document.getElementById("user-modal-title").textContent = "新建用户";
        document.getElementById("user-username").value = "";
        document.getElementById("user-password").value = "";
        document.getElementById("user-username").disabled = false;
        document.getElementById("user-error").textContent = "";
        loadUserGroupChecks([], false);
        document.getElementById("user-modal").style.display = "flex";
    }

    function openEditModal(id, name) {
        editingUserId = id;
        const u = users.find(function (x) { return x.id === id; });
        document.getElementById("user-modal-title").textContent = "编辑用户: " + name;
        document.getElementById("user-username").value = name;
        document.getElementById("user-password").value = "";
        document.getElementById("user-username").disabled = true;
        document.getElementById("user-error").textContent = "";
        if (canAssign()) {
            API.get("/users/" + id + "/permissions").then(function (info) {
                const cur = (info.groups || []).map(function (g) { return g.id; });
                loadUserGroupChecks(cur, false);
            }).catch(function () {
                loadUserGroupChecks([], true);
            });
        } else {
            loadUserGroupChecks([], true);
        }
        document.getElementById("user-modal").style.display = "flex";
    }

    function loadUserGroupChecks(selected, disabled) {
        const box = document.getElementById("user-groups-box");
        if (!canAssign()) {
            box.innerHTML = '<span class="lc-muted">无权限查看/分配权限组</span>';
            box.dataset.ready = "0";
            return;
        }
        API.get("/permission-groups").then(function (g) {
            groups = g || [];
            box.dataset.ready = "1";
            if (!groups.length) {
                box.innerHTML = '<span class="lc-muted">暂无权限组</span>';
                return;
            }
            box.innerHTML = groups.map(function (grp) {
                return '<label class="lc-perm-check"><input type="checkbox" value="' + grp.id + '"' +
                    (selected.indexOf(grp.id) !== -1 ? " checked" : "") + (disabled ? " disabled" : "") + '> ' +
                    escapeHtml(grp.name) +
                    (grp.groupId ? ' <code class="gid">' + escapeHtml(grp.groupId) + '</code>' : '') +
                    '</label>';
            }).join("");
        }).catch(function () {
            box.innerHTML = '<span class="lc-muted">权限组加载失败</span>';
            box.dataset.ready = "0";
        });
    }

    function saveUser() {
        const username = document.getElementById("user-username").value.trim();
        const password = document.getElementById("user-password").value;
        if (!username) { document.getElementById("user-error").textContent = "请输入用户名"; return; }
        const lu = username.toLowerCase();
        if (lu === "ling" || lu === "root" || lu === "lingconsole") { document.getElementById("user-error").textContent = "该用户名已被保留, 不可使用"; return; }
        if (!editingUserId && password.length < 6) { document.getElementById("user-error").textContent = "密码至少 6 位"; return; }
        const body = { username: username };
        if (password) body.password = password;
        const gbox = document.getElementById("user-groups-box");
        if (canAssign() && gbox.dataset.ready === "1") {
            body.groupIds = Array.prototype.map.call(
                gbox.querySelectorAll("input:checked"),
                function (c) { return c.value; });
        }

        const req = editingUserId
            ? API.put("/users/" + editingUserId, body)
            : API.post("/users", body);
        req.then(function () {
            closeModal("user-modal");
            loadUsers();
        }).catch(function (e) {
            document.getElementById("user-error").textContent = e.message || "操作失败";
        });
    }

    async function deleteUser(id, name) {
        if (!await LC.dialog.confirm("确认删除用户 " + name + "?")) return;
        API.del("/users/" + id).then(function () {
            loadUsers();
        }).catch(function (e) {
            LC.dialog.alert(e.message || "删除失败");
        });
    }





    function loadGroupsAndKeys() {
        return Promise.all([
            API.get("/permission-groups").then(function (g) { groups = g || []; }),
            API.get("/permission-keys").then(function (k) { permKeys = k || {}; })
        ]);
    }

    function openGroupsModal() {
        loadGroupsAndKeys().then(function () {
            document.getElementById("groups-list").innerHTML =
                '<div class="lc-perm-box">' + groups.map(function (g) {
                    const perms = (g.permissions || []).map(function (p) {
                        return '<span class="lc-perm-chip">' + escapeHtml(permLabels()[p] || p) + '</span>';
                    }).join("");
                    return '<div class="lc-group-row">' +
                        '<div class="lc-group-info"><strong>' + escapeHtml(g.name) + '</strong>' +
                        (g.groupId ? ' <code class="gid">' + escapeHtml(g.groupId) + '</code>' : '') +
                        '<div class="lc-group-desc">' + escapeHtml(g.description || "") + '</div>' +
                        '<div>' + perms + '</div></div>' +
                        '<div class="lc-group-actions">' +
                        '<button class="lc-btn lc-btn--sm" data-gid="' + g.id + '" data-action="edit-group">编辑</button> ' +
                        '<button class="lc-btn lc-btn--sm lc-btn--danger" data-gid="' + g.id + '" data-action="del-group">删除</button>' +
                        '</div></div>';
                }).join("") + '</div>';
            document.getElementById("groups-list").querySelectorAll("button[data-action]").forEach(function (btn) {
                btn.addEventListener("click", function () {
                    const gid = btn.getAttribute("data-gid");
                    if (btn.getAttribute("data-action") === "edit-group") openGroupModal(gid);
                    else deleteGroup(gid);
                });
            });
            document.getElementById("groups-modal").style.display = "flex";
        }).catch(function (e) { LC.dialog.alert(e.message); });
    }

    async function deleteGroup(gid) {
        if (!await LC.dialog.confirm("确认删除该权限组? 相关用户将失去该组权限")) return;
        API.del("/permission-groups/" + gid).then(openGroupsModal).catch(function (e) { LC.dialog.alert(e.message); });
    }

    let editingGroupId = null;
    function openGroupModal(gid) {
        editingGroupId = gid || null;
        const g = groups.find(function (x) { return x.id === gid; });
        document.getElementById("group-modal-title").textContent = g ? "编辑权限组: " + g.name : "新建权限组";
        document.getElementById("group-groupid").value = g ? (g.groupId || "") : "";
        document.getElementById("group-name").value = g ? g.name : "";
        document.getElementById("group-desc").value = g ? (g.description || "") : "";
        renderGroupPermCheckboxes(g ? (g.permissions || []) : []);
        document.getElementById("group-error").textContent = "";
        document.getElementById("group-modal").style.display = "flex";
    }

    function renderGroupPermCheckboxes(selected) {
        const box = document.getElementById("group-perms-box");
        const labels = permLabels();
        const keys = (permKeys.grantable || []);
        box.innerHTML = keys.map(function (k) {
            return '<label class="lc-perm-check"><input type="checkbox" value="' + k + '"' +
                (selected.indexOf(k) !== -1 ? " checked" : "") + '> ' + escapeHtml(labels[k] || k) + '</label>';
        }).join("");
    }

    function saveGroup() {
        const name = document.getElementById("group-name").value.trim();
        const desc = document.getElementById("group-desc").value.trim();
        const groupId = document.getElementById("group-groupid").value.trim().toLowerCase();
        const perms = Array.prototype.map.call(
            document.querySelectorAll("#group-perms-box input:checked"),
            function (c) { return c.value; });
        if (!name) { document.getElementById("group-error").textContent = "请输入名称"; return; }
        if (!groupId) { document.getElementById("group-error").textContent = "请输入权限组 ID (仅小写英文字母)"; return; }
        if (!/^[a-z]+$/.test(groupId)) {
            document.getElementById("group-error").textContent = "权限组 ID 仅允许小写英文字母 (当前: " + groupId + ")";
            return;
        }
        const body = { groupId: groupId, name: name, description: desc, permissions: perms };
        const req = editingGroupId
            ? API.put("/permission-groups/" + editingGroupId, body)
            : API.post("/permission-groups", body);
        req.then(function () {
            closeModal("group-modal");
            openGroupsModal();
        }).catch(function (e) {
            document.getElementById("group-error").textContent = e.message || "操作失败";
        });
    }

    function permLabels() {
        return (permKeys.labels) || {};
    }





    function openPermModal(userId, name) {
        document.getElementById("perm-modal-title").textContent = "用户权限: " + name;
        document.getElementById("perm-error").textContent = "";
        Promise.all([
            loadGroupsAndKeys(),
            API.get("/users/" + userId + "/permissions")
        ]).then(function (res) {
            const info = res[1];

            const userGroupIds = (info.groups || []).map(function (g) { return g.id; });
            document.getElementById("perm-groups-box").innerHTML =
                groups.map(function (g) {
                    return '<label class="lc-perm-check"><input type="checkbox" value="' + g.id + '"' +
                        (userGroupIds.indexOf(g.id) !== -1 ? " checked" : "") + '> ' + escapeHtml(g.name) +
                        (g.groupId ? ' <code class="gid">' + escapeHtml(g.groupId) + '</code>' : '') + '</label>';
                }).join("");

            editingUserId = userId;
            document.getElementById("perm-modal").style.display = "flex";
        }).catch(function (e) { LC.dialog.alert(e.message); });
    }

    function savePerm() {
        const userId = editingUserId;
        const groupIds = Array.prototype.map.call(
            document.querySelectorAll("#perm-groups-box input:checked"),
            function (c) { return c.value; });
        API.put("/users/" + userId + "/groups", { groupIds: groupIds }).then(function () {
            closeModal("perm-modal");
            LC.dialog.alert("权限已保存");
            loadUsers();
        }).catch(function (e) {
            document.getElementById("perm-error").textContent = e.message || "保存失败";
        });
    }





    function closeModal(id) {
        document.getElementById(id).style.display = "none";
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }
    function escapeAttr(s) {
        return escapeHtml(s).replace(/"/g, "&quot;");
    }


    document.getElementById("btn-add-user").addEventListener("click", openCreateModal);
    document.getElementById("btn-close-user-modal").addEventListener("click", function () { closeModal("user-modal"); });
    document.getElementById("btn-cancel-user-modal").addEventListener("click", function () { closeModal("user-modal"); });
    document.getElementById("btn-save-user").addEventListener("click", saveUser);

    document.getElementById("btn-groups").addEventListener("click", function () {
        window.location.href = "/permission-groups";
    });
    document.getElementById("btn-close-groups-modal").addEventListener("click", function () { closeModal("groups-modal"); });
    document.getElementById("btn-close-groups-modal2").addEventListener("click", function () { closeModal("groups-modal"); });
    document.getElementById("btn-add-group").addEventListener("click", function () { openGroupModal(null); });
    document.getElementById("btn-close-group-modal").addEventListener("click", function () { closeModal("group-modal"); });
    document.getElementById("btn-cancel-group-modal").addEventListener("click", function () { closeModal("group-modal"); });
    document.getElementById("btn-save-group").addEventListener("click", saveGroup);

    document.getElementById("btn-close-perm-modal").addEventListener("click", function () { closeModal("perm-modal"); });
    document.getElementById("btn-cancel-perm-modal").addEventListener("click", function () { closeModal("perm-modal"); });
    document.getElementById("btn-save-perm").addEventListener("click", savePerm);


    if (!canAssign()) {
        document.getElementById("btn-groups").style.display = "none";
    }

    window.app.loadNodes().then(loadUsers).catch(function () { loadUsers(); });
})();
