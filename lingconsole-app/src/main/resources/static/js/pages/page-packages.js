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
    let NODE_ID = "";
    const urlNodeId = window.location.pathname.split("/")[2] || "";

    function api(path) {
        return "/nodes/" + NODE_ID + "/packages" + path;
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }
    function escapeAttr(s) {
        return escapeHtml(s).replace(/"/g, "&quot;");
    }

    function show(id) { document.getElementById(id).style.display = ""; }
    function hide(id) { document.getElementById(id).style.display = "none"; }

    function setHeaderButtons(visible) {
        ["btn-install", "btn-update", "btn-upgrade", "btn-full-upgrade", "btn-autoremove"].forEach(function (id) {
            const el = document.getElementById(id);
            if (el) el.style.display = visible ? "" : "none";
        });
    }

    function showNoNode() {
        hide("unsupported");
        hide("pkg-body");
        show("no-node");
        setHeaderButtons(false);
    }

    function loadStatus() {
        API.get(api("/status")).then(function (info) {
            if (info.supported) {
                hide("no-node");
                hide("unsupported");
                show("pkg-body");
                setHeaderButtons(true);
                loadPackages();
            } else {
                hide("no-node");
                hide("pkg-body");
                show("unsupported");
                setHeaderButtons(false);
                document.getElementById("unsupported-desc").textContent =
                    info.osName ? ("检测到系统: " + info.osName) : "仅支持 Debian 系 (Debian/Ubuntu 等)";
            }
        }).catch(function (e) {
            LC.dialog.alert(e.message || "检测系统失败");
        });
    }

    function loadPackages() {
        API.get(api("")).then(function (data) {
            const list = data.packages || [];
            document.getElementById("pkg-count").textContent = list.length;
            const tbody = document.getElementById("pkg-tbody");
            if (!list.length) {
                tbody.innerHTML = '<tr><td colspan="3" class="lc-table__empty">暂无已安装的包</td></tr>';
                return;
            }
            tbody.innerHTML = list.map(function (p) {
                return '<tr>' +
                    '<td>' + escapeHtml(p.name) + '</td>' +
                    '<td>' + escapeHtml(p.version || "--") + '</td>' +
                    '<td><button class="lc-btn lc-btn--sm lc-btn--danger" data-name="' + escapeAttr(p.name) + '">卸载</button></td>' +
                    '</tr>';
            }).join("");
            tbody.querySelectorAll("button[data-name]").forEach(function (b) {
                b.addEventListener("click", function () { removePackage(b.getAttribute("data-name")); });
            });
        }).catch(function (e) {
            document.getElementById("pkg-tbody").innerHTML =
                '<tr><td colspan="3" class="lc-table__empty">加载失败: ' + escapeHtml(e.message) + '</td></tr>';
        });
    }

    let overlay = null;
    function showOverlay(text) {
        if (!overlay) {
            overlay = document.createElement("div");
            overlay.className = "lc-pkg-overlay";
            overlay.innerHTML =
                '<div class="lc-pkg-card">' +
                '<div class="lc-pkg-status"></div>' +
                '<div class="lc-pkg-text"></div>' +
                '<button class="lc-btn lc-btn--sm lc-pkg-logbtn" style="display:none">查看日志</button>' +
                '</div>';
            document.body.appendChild(overlay);
        }
        const st = overlay.querySelector(".lc-pkg-status");
        st.className = "lc-pkg-status lc-pkg-status--spin";
        st.textContent = "";
        overlay.querySelector(".lc-pkg-text").textContent = text;
        const btn = overlay.querySelector(".lc-pkg-logbtn");
        btn.style.display = "none";
        btn.onclick = null;
        overlay.style.display = "flex";
    }
    function showResult(ok, text, logFn) {
        if (!overlay) return;
        const st = overlay.querySelector(".lc-pkg-status");
        st.className = "lc-pkg-status " + (ok ? "lc-pkg-status--ok" : "lc-pkg-status--fail");
        st.textContent = ok ? "✓" : "✗";
        overlay.querySelector(".lc-pkg-text").textContent = text;
        const btn = overlay.querySelector(".lc-pkg-logbtn");
        if (!ok && logFn) {
            btn.style.display = "";
            btn.onclick = function () {
                hideOverlay();
                logFn();
            };
        }
    }
    function hideOverlay() {
        if (overlay) overlay.style.display = "none";
    }

    function runOp(action, body, runningLabel, okLabel, failLabel, refresh) {
        showOverlay(runningLabel);
        API.post(api("/" + action), body).then(function (r) {
            if (refresh) refresh();
            if (r.exitCode === 0) {
                showResult(true, okLabel);
                setTimeout(hideOverlay, 1400);
            } else {
                showResult(false, failLabel, function () {
                    openLog((r.output || "无输出") + (r.timedOut ? "\n[执行超时]" : ""));
                });
            }
        }).catch(function (e) {
            showResult(false, failLabel, function () { openLog(e.message || "无输出"); });
        });
    }

    function installPackage(name) {
        showOverlay("正在安装 " + name + " ...");
        API.post(api("/install"), { name: name }).then(function (r) {
            loadPackages();
            if (r.exitCode === 0) {
                showResult(true, "已安装 " + name);
                setTimeout(hideOverlay, 1500);
            } else {
                showResult(false, "安装失败", function () {
                    openLog((r.output || "无输出") + (r.timedOut ? "\n[执行超时]" : ""));
                });
            }
        }).catch(function (e) {
            showResult(false, "安装失败", function () { openLog(e.message || "无输出"); });
        });
    }

    async function removePackage(name) {
        if (!await LC.dialog.confirm("确认卸载 " + name + "?")) return;
        runOp("remove", { name: name }, "正在卸载 " + name + " ...", "已卸载 " + name, "卸载失败", loadPackages);
    }

    async function startInstall() {
        const kw = await LC.dialog.prompt("搜索软件包 (输入关键字):");
        if (!kw || !kw.trim()) return;
        try {
            const data = await API.post(api("/search"), { name: kw.trim() });
            const pkgs = data.packages || [];
            if (!pkgs.length) {
                LC.dialog.alert("未找到与 '" + kw.trim() + "' 相关的软件包");
                return;
            }
            showSearchResults(kw.trim(), pkgs);
        } catch (e) {
            LC.dialog.alert(e.message || "搜索失败");
        }
    }

    function showSearchResults(kw, pkgs) {
        document.getElementById("search-hint").textContent =
            "关键字: " + kw + " — 共 " + pkgs.length + " 个结果, 点击选择要安装的软件包";
        document.getElementById("search-list").innerHTML = pkgs.map(function (p) {
            return '<div class="lc-pkg-search-item" data-name="' + escapeAttr(p.name) + '">' +
                '<div class="lc-pkg-search-main"><strong>' + escapeHtml(p.name) + '</strong>' +
                '<div class="lc-pkg-search-desc">' + escapeHtml(p.description || "") + '</div></div>' +
                '<button class="lc-btn lc-btn--primary lc-btn--sm">安装</button>' +
                '</div>';
        }).join("");
        document.getElementById("search-list").querySelectorAll(".lc-pkg-search-item").forEach(function (el) {
            el.addEventListener("click", function () {
                const name = el.getAttribute("data-name");
                document.getElementById("search-modal").style.display = "none";
                installPackage(name);
            });
        });
        document.getElementById("search-modal").style.display = "flex";
    }

    function openLog(output) {
        document.getElementById("log-content").textContent = output || "无输出";
        document.getElementById("log-modal").style.display = "flex";
    }

    function init() {
        window.app.loadNodes().then(function (nodes) {
            NODE_ID = urlNodeId || window.app.currentNode() || "";
            if (urlNodeId) window.app.setCurrentNode(urlNodeId);
            const selector = document.getElementById("node-selector");
            if (!nodes.length) {
                showNoNode();
                selector.innerHTML = '<option value="">无节点</option>';
                return;
            }
            selector.innerHTML = nodes.map(function (n) {
                return '<option value="' + escapeAttr(n.id) + '"' + (n.id === NODE_ID ? " selected" : "") + '>' +
                    escapeHtml(n.name) + '</option>';
            }).join("");
            if (!NODE_ID || !nodes.some(function (n) { return n.id === NODE_ID; })) {
                showNoNode();
                selector.disabled = true;
                return;
            }
            selector.addEventListener("change", function () {
                window.app.setCurrentNode(selector.value);
                window.app.renderSidebarNode();
                window.location.href = "/packages/" + selector.value;
            });
            loadStatus();
        }).catch(function () {
            showNoNode();
        });
    }

    document.getElementById("btn-install").addEventListener("click", startInstall);
    document.getElementById("btn-update").addEventListener("click", function () {
        runOp("update", {}, "正在更新软件源 ...", "软件源更新完成", "更新失败", null);
    });
    document.getElementById("btn-upgrade").addEventListener("click", function () {
        runOp("upgrade", {}, "正在升级软件包 ...", "升级完成", "升级失败", loadPackages);
    });
    document.getElementById("btn-full-upgrade").addEventListener("click", function () {
        runOp("full-upgrade", {}, "正在智能升级 ...", "智能升级完成", "智能升级失败", loadPackages);
    });
    document.getElementById("btn-autoremove").addEventListener("click", function () {
        runOp("autoremove", {}, "正在清理无用依赖 ...", "清理完成", "清理失败", loadPackages);
    });
    document.getElementById("btn-refresh").addEventListener("click", loadPackages);

    document.getElementById("btn-close-search").addEventListener("click", function () {
        document.getElementById("search-modal").style.display = "none";
    });
    document.getElementById("btn-close-log").addEventListener("click", function () {
        document.getElementById("log-modal").style.display = "none";
    });
    document.getElementById("btn-close-log2").addEventListener("click", function () {
        document.getElementById("log-modal").style.display = "none";
    });

    window.app.onReady(init);
})();
