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
    const parts = window.location.pathname.split("/");
    const APP_MODE = parts[2] === "app";
    const NODE_ID = APP_MODE ? parts[3] : parts[2];
    const APP_ID = APP_MODE ? parts[4] : "";

    let currentPath = APP_MODE ? "" : "/";
    let nodeStyle = "auto";      // 节点系统偏好
    let detectedOs = "";         // auto 模式下由 drives 端点探测
    let selected = new Map();    // 多选: 已选条目路径 -> {path, name, dir}

    function api(path) {
        const base = APP_MODE
            ? "/nodes/" + NODE_ID + "/apps/" + APP_ID + "/files"
            : "/nodes/" + NODE_ID + "/files";
        return base + path;
    }

    
    function effectiveOs() {
        if (nodeStyle === "windows") return "windows";
        if (nodeStyle === "linux") return "linux";
        return detectedOs;
    }





    function load(path) {
        currentPath = path != null ? path : (APP_MODE ? "" : "/");
        if (currentPath === "drives") {
            loadDrives();
            return;
        }
        API.get(api("/list?path=" + encodeURIComponent(currentPath))).then(function (entries) {
            render(entries || []);
            renderBreadcrumb();
            setToolbarVisible(true);
        }).catch(function (e) {
            renderError(e.message);
        });
    }

    function loadDrives() {
        currentPath = "drives";
        API.get(api("/drives")).then(function (info) {
            detectedOs = info.os || "";
            renderDrives(info.drives || []);
            renderBreadcrumb();
            setToolbarVisible(false);
        }).catch(function (e) {
            renderError(e.message);
        });
    }

    
    function goRoot() {
        if (APP_MODE) { load(""); return; }
        if (nodeStyle === "linux") { load("/"); return; }
        if (nodeStyle === "windows") { loadDrives(); return; }

        API.get(api("/drives")).then(function (info) {
            detectedOs = info.os || "";
            if (info.os === "windows") {
                loadDrives();
            } else {
                load("/");
            }
        }).catch(function () {
            load("/");
        });
    }

    
    function goUp() {
        if (currentPath === "drives") return;
        if (APP_MODE) {
            const crumbs = currentPath.split("/").filter(Boolean);
            crumbs.pop();
            load(crumbs.join("/"));
            return;
        }
        if (effectiveOs() === "windows") {
            const parts2 = currentPath.split("\\").filter(Boolean);
            parts2.pop();
            if (!parts2.length) { loadDrives(); return; }
            load(parts2.join("\\") + "\\");
            return;
        }
        const parts2 = currentPath.split("/").filter(Boolean);
        parts2.pop();
        load("/" + parts2.join("/"));
    }

    function setToolbarVisible(visible) {
        ["btn-new-dir", "btn-new-file", "btn-upload"].forEach(function (id) {
            const el = document.getElementById(id);
            if (el) el.style.display = visible ? "" : "none";
        });
    }





    let allEntries = [];
    let page = 1;
    let pageSize = 100;

    function render(entries) {
        allEntries = entries || [];
        page = 1;
        renderPage();
    }

    function currentPageEntries() {
        const total = allEntries.length;
        if (!total) return allEntries;
        if (pageSize === "all") return allEntries;
        const pageCount = Math.max(1, Math.ceil(total / pageSize));
        if (page > pageCount) page = pageCount;
        const start = (page - 1) * pageSize;
        return allEntries.slice(start, start + pageSize);
    }

    function updatePager() {
        const info = document.getElementById("pager-info");
        const prev = document.getElementById("btn-pager-prev");
        const next = document.getElementById("btn-pager-next");
        const total = allEntries.length;
        if (!info || !prev || !next) return;
        if (pageSize === "all") {
            info.textContent = "共 " + total + " 项";
            prev.style.display = "none";
            next.style.display = "none";
            return;
        }
        prev.style.display = "";
        next.style.display = "";
        const pageCount = Math.max(1, Math.ceil(total / pageSize));
        const start = total === 0 ? 0 : (page - 1) * pageSize + 1;
        const end = Math.min(page * pageSize, total);
        info.textContent = "第 " + start + "-" + end + " / 共 " + total + " 项 (第 " + page + "/" + pageCount + " 页)";
        prev.disabled = page <= 1;
        next.disabled = page >= pageCount;
    }

    function renderPage() {
        const tbody = document.getElementById("file-tbody");
        const entries = currentPageEntries();
        if (!allEntries.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="lc-table__empty">空目录</td></tr>';
            updatePager();
            updateBatchBar();
            return;
        }
        tbody.innerHTML = entries.map(function (e) {
            const icon = e.directory ? "📁" : "📄";
            const size = e.directory ? "--" : window.app.formatSize(e.size);
            const checked = selected.has(e.path) ? " checked" : "";
            const extractBtn = (!e.directory && isArchiveName(e.name))
                ? '<button class="lc-btn lc-btn--sm" data-action="extract" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '">解压</button> '
                : "";
            const actions = e.directory
                ? '<button class="lc-btn lc-btn--sm" data-action="enter" data-path="' + encodeURIComponent(e.path) + '">进入</button> ' +
                  '<button class="lc-btn lc-btn--sm" data-action="compress" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '" data-dir="1">压缩</button> ' +
                  '<button class="lc-btn lc-btn--sm" data-action="copy" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '" data-dir="1">复制</button> ' +
                  '<button class="lc-btn lc-btn--sm" data-action="rename" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '">重命名</button> ' +
                  '<button class="lc-btn lc-btn--sm lc-btn--danger" data-action="delete" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '">删除</button>'
                : '<button class="lc-btn lc-btn--sm" data-action="edit" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '" data-size="' + e.size + '">编辑</button> ' +
                  '<button class="lc-btn lc-btn--sm" data-action="download" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '">下载</button> ' +
                  '<button class="lc-btn lc-btn--sm" data-action="compress" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '" data-dir="0">压缩</button> ' +
                  extractBtn +
                  '<button class="lc-btn lc-btn--sm" data-action="copy" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '" data-dir="0">复制</button> ' +
                  '<button class="lc-btn lc-btn--sm" data-action="rename" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '">重命名</button> ' +
                  '<button class="lc-btn lc-btn--sm lc-btn--danger" data-action="delete" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '">删除</button>';
            return '<tr class="' + (checked ? "lc-file-row--sel" : "") + '" data-path="' + encodeURIComponent(e.path) + '">' +
                '<td><input type="checkbox" class="lc-file-check" data-path="' + encodeURIComponent(e.path) + '" data-name="' + escapeAttr(e.name) + '" data-dir="' + (e.directory ? "1" : "0") + '"' + checked + '></td>' +
                '<td><span class="lc-file-icon">' + icon + '</span> <span data-action="open" data-path="' + encodeURIComponent(e.path) + '" data-dir="' + e.directory + '" data-size="' + e.size + '" class="lc-file-name">' + escapeHtml(e.name) + '</span></td>' +
                '<td>' + size + '</td>' +
                '<td>' + window.app.formatTime(e.modified) + '</td>' +
                '<td>' + actions + '</td>' +
                '</tr>';
        }).join("");

        tbody.querySelectorAll(".lc-file-check").forEach(function (cb) {
            cb.addEventListener("change", function () {
                const p = decodeURIComponent(cb.getAttribute("data-path"));
                if (cb.checked) {
                    selected.set(p, { name: cb.getAttribute("data-name"), dir: cb.getAttribute("data-dir") === "1" });
                } else {
                    selected.delete(p);
                }
                syncSelectionStyles();
            });
        });
        tbody.querySelectorAll("[data-action]").forEach(function (el) {
            el.addEventListener("click", function (e) {
                e.stopPropagation();
                const action = el.getAttribute("data-action");
                const path = decodeURIComponent(el.getAttribute("data-path"));
                const name = el.getAttribute("data-name");
                const size = parseInt(el.getAttribute("data-size"), 10) || 0;
                const isDir = el.getAttribute("data-dir") === "true" || el.getAttribute("data-dir") === "1";
                if (action === "enter" || (action === "open" && isDir)) { clearSelection(); load(path); }
                else if (action === "open") openEditor(path, name, size);
                else if (action === "edit") openEditor(path, name, size);
                else if (action === "download") download(path, name);
                else if (action === "copy") copyEntry(path, name);
                else if (action === "compress") compressEntry(path, name);
                else if (action === "extract") extractEntry(path, name);
                else if (action === "delete") remove(path, name);
                else if (action === "rename") renameEntry(path, name);
            });
        });
        updatePager();
        updateBatchBar();
    }

    function clearSelection() {
        selected.clear();
        syncSelectionStyles();
    }

    function syncSelectionStyles() {
        document.querySelectorAll("#file-tbody tr[data-path]").forEach(function (tr) {
            const p = decodeURIComponent(tr.getAttribute("data-path"));
            tr.classList.toggle("lc-file-row--sel", selected.has(p));
        });
        updateBatchBar();
    }

    function updateBatchBar() {
        const bar = document.getElementById("batch-bar");
        if (!bar) return;
        const count = selected.size;
        bar.style.display = count > 0 ? "" : "none";
        const el = document.getElementById("sel-count");
        if (el) el.textContent = "已选 " + count + " 项";
        const checkAll = document.getElementById("check-all");
        if (checkAll) {
            const total = document.querySelectorAll("#file-tbody .lc-file-check").length;
            checkAll.checked = total > 0 && count === total;
            checkAll.indeterminate = count > 0 && count < total;
        }
    }

    
    function renderDrives(drives) {
        const tbody = document.getElementById("file-tbody");
        allEntries = [];
        if (!drives.length) {
            tbody.innerHTML = '<tr><td colspan="5" class="lc-table__empty">无可用盘符</td></tr>';
            updatePager();
            updateBatchBar();
            return;
        }
        tbody.innerHTML = drives.map(function (d) {
            return '<tr>' +
                '<td></td>' +
                '<td><span class="lc-file-icon">💽</span> <span class="lc-file-name" data-drive="' + encodeURIComponent(d.path) + '">' + escapeHtml(d.name) + '</span></td>' +
                '<td>盘符</td>' +
                '<td>--</td>' +
                '<td><button class="lc-btn lc-btn--sm" data-drive="' + encodeURIComponent(d.path) + '">进入</button></td>' +
                '</tr>';
        }).join("");
        tbody.querySelectorAll("[data-drive]").forEach(function (el) {
            el.addEventListener("click", function () {
                load(decodeURIComponent(el.getAttribute("data-drive")));
            });
        });
        updatePager();
        updateBatchBar();
    }

    function renderBreadcrumb() {
        const el = document.getElementById("breadcrumb");
        if (!el) return;

        let html = "";
        if (APP_MODE) {

            html = '<span data-path="" class="lc-crumb">(工作目录)</span>';
            let acc = "";
            currentPath.split("/").filter(Boolean).forEach(function (p) {
                acc = acc === "" ? p : acc + "/" + p;
                html += '<span class="lc-crumb-sep">/</span><span data-path="' + acc + '" class="lc-crumb">' + escapeHtml(p) + '</span>';
            });
        } else if (currentPath === "drives") {
            html = '<span data-path="drives" class="lc-crumb">根</span>';
        } else if (effectiveOs() === "windows") {

            html = '<span data-path="drives" class="lc-crumb">根</span>';
            let acc = "";
            currentPath.split("\\").filter(Boolean).forEach(function (p, i) {
                acc = i === 0 ? p + "\\" : acc + p + "\\";
                html += '<span class="lc-crumb-sep">\\</span><span data-path="' + acc + '" class="lc-crumb">' + escapeHtml(p) + '</span>';
            });
        } else {

            html = '<span data-path="/" class="lc-crumb">根</span>';
            let acc = "";
            currentPath.split("/").filter(Boolean).forEach(function (p) {
                acc += "/" + p;
                html += '<span class="lc-crumb-sep">/</span><span data-path="' + acc + '" class="lc-crumb">' + escapeHtml(p) + '</span>';
            });
        }
        el.innerHTML = html;
        bindCrumbs(el);
    }

    function bindCrumbs(el) {
        el.querySelectorAll(".lc-crumb").forEach(function (c) {
            c.addEventListener("click", function () {
                const dp = c.getAttribute("data-path");
                if (dp === "drives" || dp === "/" || dp === "") { goRoot(); }
                else { load(dp); }
            });
        });
    }

    function renderError(msg) {
        const tbody = document.getElementById("file-tbody");
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="5" class="lc-table__empty">加载失败: ' + escapeHtml(msg) + '</td></tr>';
        }
        updateBatchBar();
    }





    let editor = null;
    let editingPath = "";
    let editingOriginal = "";

    function openEditor(path, name, size) {
        if (size && size > 100 * 1024) {
            LC.dialog.alert("文件过大 (" + window.app.formatSize(size) + " > 100KB)\n无法在线打开, 请下载后本地修改替换");
            return;
        }
        editingPath = path;
        document.getElementById("editor-title").textContent = "编辑: " + (name || path);
        document.getElementById("editor-error").textContent = "";
        document.getElementById("editor-modal").style.display = "flex";

        API.get(api("/read?path=" + encodeURIComponent(path))).then(function (data) {
            const content = data.content || "";
            editingOriginal = content;
            if (!editor) {
                editor = CodeMirror.fromTextArea(document.getElementById("editor-area"), {
                    lineNumbers: true,
                    lineWrapping: false,
                    matchBrackets: true,
                    indentUnit: 4,
                    mode: detectMode(name || path)
                });
                setTimeout(function () { editor.refresh(); }, 50);
            }
            editor.setValue(content);
            editor.setOption("readOnly", false);
            setTimeout(function () { editor.focus(); editor.setCursor({ line: 0, ch: 0 }); }, 60);
        }).catch(function (e) {
            document.getElementById("editor-error").textContent = e.message;
        });
    }

    function detectMode(name) {
        if (/\.(sh|bash|zsh)$/.test(name)) return "shell";
        if (/\.(yml|yaml)$/.test(name)) return "yaml";
        if (/\.toml$/.test(name)) return "toml";
        if (/\.(json)$/.test(name)) return "javascript";
        if (/\.(java|js|ts|py|xml|html|css)$/.test(name)) return "shell";
        return "shell";
    }

    function saveEditor() {
        if (!editingPath || !editor) return;
        if (editor.getValue() === editingOriginal) {
            LC.dialog.alert("文件未变更！取消！");
            return;
        }
        API.post(api("/write"), { path: editingPath, content: editor.getValue() })
            .then(function () {
                document.getElementById("editor-modal").style.display = "none";
            })
            .catch(function (e) {
                document.getElementById("editor-error").textContent = e.message;
            });
    }

    function closeEditor() {
        document.getElementById("editor-modal").style.display = "none";
    }





    function download(path, name) {
        const a = document.createElement("a");
        a.href = "/api" + api("/download?path=" + encodeURIComponent(path));
        a.download = name || "download";
        document.body.appendChild(a);
        a.click();
        a.remove();
    }

    async function remove(path, name) {
        if (!await LC.dialog.confirm("确认删除 " + name + "?")) return;
        API.del(api("?path=" + encodeURIComponent(path)))
            .then(function () { load(currentPath); })
            .catch(function (e) { LC.dialog.alert(e.message || "删除失败"); });
    }

    async function renameEntry(path, name) {
        const newName = await LC.dialog.prompt("重命名 '" + name + "' 为:", name);
        if (!newName || newName === name) return;
        API.post(api("/rename?path=" + encodeURIComponent(path) + "&newName=" + encodeURIComponent(newName)), undefined)
            .then(function () { load(currentPath); })
            .catch(function (e) { LC.dialog.alert(e.message || "重命名失败"); });
    }

    async function copyEntry(path, name) {
        const newName = await LC.dialog.prompt("复制 '" + name + "' 为:", name);
        if (!newName) return;
        const dest = joinPath(currentPath, newName);
        API.post(api("/copy?path=" + encodeURIComponent(path) + "&dest=" + encodeURIComponent(dest)), undefined)
            .then(function () { load(currentPath); })
            .catch(function (e) { LC.dialog.alert(e.message || "复制失败"); });
    }

    function checkAllToggle() {
        const boxes = document.querySelectorAll("#file-tbody .lc-file-check");
        const allChecked = boxes.length > 0 && Array.prototype.every.call(boxes, function (c) { return c.checked; });
        boxes.forEach(function (cb) {
            cb.checked = !allChecked;
            const p = decodeURIComponent(cb.getAttribute("data-path"));
            if (!allChecked) {
                selected.set(p, { name: cb.getAttribute("data-name"), dir: cb.getAttribute("data-dir") === "1" });
            } else {
                selected.delete(p);
            }
        });
        syncSelectionStyles();
    }

    async function batchDelete() {
        const count = selected.size;
        if (!count) return;
        if (!await LC.dialog.confirm("确认删除选中的 " + count + " 项?")) return;
        const items = Array.from(selected.keys());
        try {
            for (const p of items) {
                await API.del(api("?path=" + encodeURIComponent(p)));
            }
            clearSelection();
            load(currentPath);
        } catch (e) {
            LC.dialog.alert(e.message || "批量删除失败");
        }
    }

    function batchDownload() {
        const items = Array.from(selected.entries()).filter(function (kv) { return !kv[1].dir; });
        items.forEach(function (kv) {
            download(kv[0], kv[1].name);
        });
    }

    async function batchCopy() {
        const items = Array.from(selected.entries());
        if (!items.length) return;
        for (const kv of items) {
            const newName = await LC.dialog.prompt("复制 '" + kv[1].name + "' 为:", kv[1].name);
            if (!newName) continue;
            const dest = joinPath(currentPath, newName);
            try {
                await API.post(api("/copy?path=" + encodeURIComponent(kv[0]) + "&dest=" + encodeURIComponent(dest)), undefined);
            } catch (e) {
                LC.dialog.alert(e.message || "复制失败");
                return;
            }
        }
        clearSelection();
        load(currentPath);
    }

    async function batchCompress() {
        const items = Array.from(selected.entries());
        if (!items.length) return;
        const archiveName = await LC.dialog.prompt("批量压缩为 (输入压缩包文件名):", "archive.7z");
        if (!archiveName) return;
        const files = items.map(function (kv) { return kv[0]; });
        const op = { type: "compress", files: files, archive: joinPath(currentPath, archiveName), label: archiveName };
        clearSelection();
        ensureZip(op);
    }

    const ZIP_EXT = /\.(7z|zip|tar|tar\.gz|tgz|tar\.xz|txz|tar\.bz2|tbz2|gz|bz2|xz|rar)$/i;
    function isArchiveName(name) {
        return ZIP_EXT.test(name);
    }

    let pendingZip = null;
    let zipOverlay = null;

    function showZipOverlay(text) {
        if (!zipOverlay) {
            zipOverlay = document.createElement("div");
            zipOverlay.className = "lc-pkg-overlay";
            zipOverlay.innerHTML =
                '<div class="lc-pkg-card">' +
                '<div class="lc-pkg-status lc-pkg-status--spin"></div>' +
                '<div class="lc-pkg-text"></div>' +
                '</div>';
            document.body.appendChild(zipOverlay);
        }
        zipOverlay.querySelector(".lc-pkg-text").textContent = text;
        zipOverlay.style.display = "flex";
    }
    function hideZipOverlay() {
        if (zipOverlay) zipOverlay.style.display = "none";
    }

    async function compressEntry(path, name) {
        const archiveName = await LC.dialog.prompt("压缩为 (输入压缩包文件名):", name + ".7z");
        if (!archiveName) return;
        ensureZip({ type: "compress", files: [path], archive: joinPath(currentPath, archiveName), label: name });
    }

    async function extractEntry(path, name) {
        ensureZip({ type: "extract", archive: path, dest: currentPath, label: name });
    }

    async function ensureZip(op) {
        try {
            const st = await API.post(api("/7zip/status"), {});
            if (st.installed) {
                runZip(op);
            } else {
                pendingZip = op;
                document.getElementById("zip-msg").textContent = "7zip未安装！请在包管理器安装！";
                document.getElementById("zip-install-status").textContent = "";
                document.getElementById("btn-auto-install").style.display = "";
                document.getElementById("zip-modal").style.display = "flex";
            }
        } catch (e) {
            LC.dialog.alert(e.message || "检测 7zip 失败");
        }
    }

    async function autoInstallZip() {
        const btn = document.getElementById("btn-auto-install");
        btn.style.display = "none";
        const st = document.getElementById("zip-install-status");
        st.textContent = "";
        showZipOverlay("正在自动安装 7zip ...");
        try {
            const r = await API.post(api("/7zip/install"), {});
            hideZipOverlay();
            const check = await API.post(api("/7zip/status"), {});
            if (check.installed) {
                st.textContent = "7zip 安装成功!";
                const op = pendingZip;
                pendingZip = null;
                setTimeout(function () {
                    document.getElementById("zip-modal").style.display = "none";
                    if (op) runZip(op);
                }, 700);
            } else {
                st.textContent = "安装失败: " + ((r && (r.stderr || r.stdout)) || "未知错误");
                btn.style.display = "";
            }
        } catch (e) {
            hideZipOverlay();
            st.textContent = "安装失败: " + e.message;
            btn.style.display = "";
        }
    }

    function runZip(op) {
        if (op.type === "compress") {
            showZipOverlay("正在压缩 ...");
            API.post(api("/archive/compress"), { files: op.files, archive: op.archive }).then(function (r) {
                hideZipOverlay();
                if (r.exitCode === 0) {
                    LC.dialog.toast("压缩完成: " + op.label, "success");
                    load(currentPath);
                } else {
                    openZipLog("压缩失败", r);
                }
            }).catch(function (e) {
                hideZipOverlay();
                LC.dialog.alert(e.message || "压缩失败");
            });
        } else {
            showZipOverlay("正在解压 ...");
            API.post(api("/archive/extract"), { archive: op.archive, dest: op.dest }).then(function (r) {
                hideZipOverlay();
                if (r.exitCode === 0) {
                    LC.dialog.toast("解压完成: " + op.label, "success");
                    load(currentPath);
                } else {
                    openZipLog("解压失败", r);
                }
            }).catch(function (e) {
                hideZipOverlay();
                LC.dialog.alert(e.message || "解压失败");
            });
        }
    }

    function openZipLog(title, r) {
        const out = (r.stderr || "") + (r.stdout ? (r.stderr ? "\n" : "") + r.stdout : "")
            + (r.timedOut ? "\n[执行超时]" : "");
        document.getElementById("zip-log-title").textContent = title;
        document.getElementById("zip-log-content").textContent = out || "无输出";
        document.getElementById("zip-log-modal").style.display = "flex";
    }

    let createMode = "file";
    function openCreate(mode) {
        createMode = mode;
        document.getElementById("create-title").textContent = mode === "dir" ? "新建目录" : "新建文件";
        document.getElementById("create-name").value = "";
        document.getElementById("create-modal").style.display = "flex";
    }
    function closeCreate() {
        document.getElementById("create-modal").style.display = "none";
    }
    function confirmCreate() {
        const name = document.getElementById("create-name").value.trim();
        if (!name) { LC.dialog.alert("请输入名称"); return; }
        if (createMode === "dir") {
            API.post(api("/mkdir?path=" + encodeURIComponent(joinPath(currentPath, name))))
                .then(function () { closeCreate(); load(currentPath); })
                .catch(function (e) { LC.dialog.alert(e.message); });
        } else {
            API.post(api("/write"), { path: joinPath(currentPath, name), content: "" })
                .then(function () { closeCreate(); load(currentPath); })
                .catch(function (e) { LC.dialog.alert(e.message); });
        }
    }

    function joinPath(base, name) {
        if (base === "" || base === "drives") return name;
        return (base.endsWith("/") ? base : base + "/") + name;
    }

    let uploadOverlay = null;

    function showUploadProgress(file) {
        if (!uploadOverlay) {
            uploadOverlay = document.createElement("div");
            uploadOverlay.className = "lc-upload-mask";
            uploadOverlay.innerHTML =
                '<div class="lc-upload-card">' +
                '<div class="lc-upload-card__title">正在上传</div>' +
                '<div class="lc-upload-card__file"></div>' +
                '<div class="lc-upload-track"><div class="lc-upload-bar"></div></div>' +
                '<div class="lc-upload-card__pct">0%</div>' +
                '</div>';
            document.body.appendChild(uploadOverlay);
        }
        uploadOverlay.querySelector(".lc-upload-card__file").textContent = file.name;
        setUploadProgress(0);
    }

    function setUploadProgress(pct) {
        if (!uploadOverlay) return;
        const bar = uploadOverlay.querySelector(".lc-upload-bar");
        const pctEl = uploadOverlay.querySelector(".lc-upload-card__pct");
        const p = Math.max(0, Math.min(100, pct));
        if (bar) bar.style.width = p + "%";
        if (pctEl) pctEl.textContent = Math.round(p) + "%";
    }

    function hideUploadProgress() {
        if (uploadOverlay && uploadOverlay.parentNode) {
            uploadOverlay.parentNode.removeChild(uploadOverlay);
        }
        uploadOverlay = null;
    }

    function uploadFiles(files) {
        const file = files[0];
        if (!file) return;
        showUploadProgress(file);
        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/api" + api("/upload?path=" + encodeURIComponent(currentPath)));
        xhr.upload.onprogress = function (e) {
            if (e.lengthComputable) {
                setUploadProgress(e.loaded / e.total * 100);
            }
        };
        xhr.onload = function () {
            hideUploadProgress();
            if (xhr.status >= 200 && xhr.status < 300) {
                load(currentPath);
            } else {
                let msg = "HTTP " + xhr.status;
                try {
                    const j = JSON.parse(xhr.responseText);
                    if (j && j.message) msg = j.message;
                } catch (e) { }
                LC.dialog.alert("上传失败: " + msg);
            }
        };
        xhr.onerror = function () {
            hideUploadProgress();
            LC.dialog.alert("上传失败: 网络错误, 请重试");
        };
        const fd = new FormData();
        fd.append("file", file);
        xhr.send(fd);
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }
    function escapeAttr(s) {
        return escapeHtml(s).replace(/"/g, "&quot;");
    }





    function loadPageInfo() {
        const nodes = window.app.nodes();
        const node = nodes.find(function (n) { return n.id === NODE_ID; });
        nodeStyle = (node && node.style) || "auto";
        const title = document.getElementById("page-title");
        const meta = document.getElementById("node-name");
        if (title) title.textContent = APP_MODE ? "应用文件管理" : "节点文件管理";
        if (meta) {
            if (APP_MODE) {
                const prefix = node ? node.name + " / " : "";
                meta.textContent = prefix + "应用 " + APP_ID.slice(0, 8) + " (工作目录内)";
                API.get("/nodes/" + NODE_ID + "/apps/" + APP_ID).then(function (app) {
                    if (app && app.name) meta.textContent = prefix + app.name + " (工作目录内)";
                }).catch(function () {  });
            } else {
                meta.textContent = node ? "节点: " + node.name + " (全权限)" : "节点文件管理";
            }
        }
    }

    function init() {
        loadPageInfo();
        if (APP_MODE) { load(""); } else { goRoot(); }
    }


    document.getElementById("btn-up").addEventListener("click", goUp);
    document.getElementById("btn-new-dir").addEventListener("click", function () { openCreate("dir"); });
    document.getElementById("btn-new-file").addEventListener("click", function () { openCreate("file"); });
    document.getElementById("btn-upload").addEventListener("click", function () {
        document.getElementById("file-input").click();
    });
    document.getElementById("file-input").addEventListener("change", function () {
        uploadFiles(this.files);
        this.value = "";
    });
    document.getElementById("btn-close-editor").addEventListener("click", closeEditor);
    document.getElementById("btn-cancel-editor").addEventListener("click", closeEditor);
    document.getElementById("btn-save-editor").addEventListener("click", saveEditor);
    document.getElementById("btn-close-create").addEventListener("click", closeCreate);
    document.getElementById("btn-cancel-create").addEventListener("click", closeCreate);
    document.getElementById("btn-confirm-create").addEventListener("click", confirmCreate);

    document.getElementById("btn-auto-install").addEventListener("click", autoInstallZip);
    document.getElementById("btn-close-zip").addEventListener("click", function () {
        document.getElementById("zip-modal").style.display = "none";
        pendingZip = null;
    });
    document.getElementById("btn-close-zip-log").addEventListener("click", function () {
        document.getElementById("zip-log-modal").style.display = "none";
    });
    document.getElementById("btn-close-zip-log2").addEventListener("click", function () {
        document.getElementById("zip-log-modal").style.display = "none";
    });

    document.getElementById("page-size").addEventListener("change", function () {
        pageSize = this.value === "all" ? "all" : parseInt(this.value, 10) || 100;
        page = 1;
        renderPage();
    });
    document.getElementById("btn-pager-prev").addEventListener("click", function () {
        if (page > 1) { page--; renderPage(); }
    });
    document.getElementById("btn-pager-next").addEventListener("click", function () {
        page++;
        renderPage();
    });

    const checkAll = document.getElementById("check-all");
    if (checkAll) checkAll.addEventListener("change", checkAllToggle);
    document.getElementById("btn-check-all").addEventListener("click", checkAllToggle);
    document.getElementById("btn-compress-sel").addEventListener("click", batchCompress);
    document.getElementById("btn-copy-sel").addEventListener("click", batchCopy);
    document.getElementById("btn-download-sel").addEventListener("click", batchDownload);
    document.getElementById("btn-delete-sel").addEventListener("click", batchDelete);


    window.app.onReady(function () {
        window.app.loadNodes().then(init).catch(init);
    });
})();
