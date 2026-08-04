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
window.app = (function () {
    let currentUser = null;
    let nodeCache = [];
    let currentPermissions = [];
    let singleUserMode = false;


    function permMatch(pattern, key) {
        if (!pattern || !key) return false;
        if (pattern === key) return true;
        if (pattern === "*") return true;
        if (pattern.endsWith(".*")) {
            const prefix = pattern.slice(0, -2);
            return key.length > prefix.length && key.startsWith(prefix) && key.charAt(prefix.length) === ".";
        }
        const p = pattern.split(".");
        const k = key.split(".");
        if (p.length > k.length) return false;
        for (let i = 0; i < p.length; i++) {
            if (p[i] === "*") continue;
            if (p[i] !== k[i]) return false;
        }
        return true;
    }

    function hasPermission(key) {
        for (let i = 0; i < currentPermissions.length; i++) {
            if (permMatch(currentPermissions[i], key)) return true;
        }
        return false;
    }

    function isRoot() {
        return currentUser && currentUser.role === 0;
    }

    function setAuthData(data) {
        currentUser = data && data.user;
        currentPermissions = (data && data.permissions) || [];
        singleUserMode = !!(data && data.singleUserMode);
        authReadyResolve();
    }


    let authReadyResolve = null;
    const authReady = new Promise(function (resolve) { authReadyResolve = resolve; });

    function onReady(cb) {
        authReady.then(function () { cb(); });
    }

    
    function applyPermissionVisibility() {
        var userMenu = document.getElementById("menu-user-manage");
        if (userMenu) userMenu.style.display = (!singleUserMode && hasPermission("lingconsole.user.manage")) ? "" : "none";
        var permGroupsMenu = document.getElementById("menu-perm-groups");
        if (permGroupsMenu) permGroupsMenu.style.display = hasPermission("lingconsole.permission.assign") ? "" : "none";
    var addonMenu = document.getElementById("menu-addons");
    if (addonMenu) addonMenu.style.display = hasPermission("lingconsole.permission.assign") ? "" : "none";
    var packageMenu = document.getElementById("menu-packages");
    if (packageMenu) packageMenu.style.display = hasPermission("lingconsole.packages.manage") ? "" : "none";
}

    
    function loadPluginMenus() {
        API.get("/addons/menus").then(function (menus) {
            var nav = document.querySelector(".lc-sidebar__menu");
            if (!nav || !menus || !menus.length) return;
            menus.forEach(function (m) {
                var a = document.createElement("a");
                a.className = "lc-sidebar__menu-item";
                a.href = m.url;
                var icon = document.createElement("span");
                icon.className = "lc-sidebar__menu-icon";
                icon.textContent = m.icon || "▦";
                var label = document.createElement("span");
                label.textContent = m.label;
                a.appendChild(icon);
                a.appendChild(label);
                nav.appendChild(a);
            });
        }).catch(function () {  });
    }


    function currentNode() {
        return window.localStorage.getItem("ling_current_node") || "";
    }

    function setCurrentNode(nodeId) {
        window.localStorage.setItem("ling_current_node", nodeId || "");
    }

    function loadNodes() {
        return API.get("/nodes").then(function (ns) {
            nodeCache = ns || [];
            return nodeCache;
        });
    }

    function nodeName(nodeId) {
        const n = nodeCache.find(function (x) { return x.id === nodeId; });
        return n ? n.name : (nodeId || "--");
    }

    function currentNodeName() {
        return nodeName(currentNode());
    }

    function isLoginPage() {
        return window.location.pathname.replace(/\/+$/, "") === "/login";
    }

    function init() {

        const timeEl = document.getElementById("header-time");
        if (timeEl) {
            tick(timeEl);
            setInterval(() => tick(timeEl), 1000);
        }

        if (!isLoginPage()) {
            API.get("/auth/me").then(function (data) {
                setAuthData(data);
                renderUser();
                applyPermissionVisibility();
                loadPluginMenus();
            }).catch(function () {

                window.location.href = "/login";
            });

            loadNodes().then(renderSidebarNode).catch(function () {  });
        }

        const btn = document.getElementById("btn-logout");
        if (btn) {
            btn.addEventListener("click", logout);
        }

        
        document.addEventListener("click", function (e) {
            if (e.target && e.target.classList && e.target.classList.contains("lc-modal-mask")) {
                e.target.style.display = "none";
            }
        });
    }

    function tick(el) {
        const now = new Date();
        el.textContent = now.getFullYear() + "-"
            + String(now.getMonth() + 1).padStart(2, "0") + "-"
            + String(now.getDate()).padStart(2, "0") + " "
            + String(now.getHours()).padStart(2, "0") + ":"
            + String(now.getMinutes()).padStart(2, "0") + ":"
            + String(now.getSeconds()).padStart(2, "0");
    }

    function renderUser() {
        const el = document.getElementById("sidebar-user");
        if (el && currentUser) {
            const tag = currentUser.role === 0 ? " (根)" : "";
            el.textContent = currentUser.username + tag;
        }
    }

    function renderSidebarNode() {
        const el = document.getElementById("sidebar-node");
        if (!el) return;
        const id = currentNode();
        el.textContent = "当前节点: " + (id ? nodeName(id) : "未选择");
        el.classList.toggle("is-empty", !id);
    }

    async function logout() {
        try {
            await API.post("/auth/logout");
        } catch (e) {  }
        window.localStorage.removeItem("ling_token");
        window.location.href = "/login";
    }

    function formatSize(bytes) {
        if (bytes == null || isNaN(bytes)) return "--";
        const units = ["B", "KB", "MB", "GB", "TB"];
        let v = bytes, i = 0;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return v.toFixed(1) + " " + units[i];
    }

    function formatTime(ts) {
        if (!ts) return "--";
        const d = new Date(ts * 1000);
        return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-"
            + String(d.getDate()).padStart(2, "0") + " "
            + String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
    }

    document.addEventListener("DOMContentLoaded", init);

    return {
        formatSize: formatSize,
        formatTime: formatTime,
        logout: logout,
        currentNode: currentNode,
        setCurrentNode: setCurrentNode,
        loadNodes: loadNodes,
        nodes: function () { return nodeCache; },
        nodeName: nodeName,
        currentNodeName: currentNodeName,
        renderSidebarNode: renderSidebarNode,
        hasPermission: hasPermission,
        isRoot: isRoot,
        setAuthData: setAuthData,
        onReady: onReady
    };
})();
