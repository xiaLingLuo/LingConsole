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
    const listEl = document.getElementById("addon-list");
    let editingName = null;

    const STATE_TEXT = {
        LOADED: "已加载",
        ENABLED: "运行中",
        DISABLED: "已停用",
        ERROR: "异常"
    };

    function load() {
        API.get("/addons").then(function (addons) {
            render(addons || []);
        }).catch(function (e) {
            if (listEl) listEl.innerHTML = '<div class="lc-config-note">加载失败: ' + escapeHtml(e.message) + '</div>';
        });
    }

    function render(addons) {
        if (!listEl) return;
        if (!addons.length) {
            listEl.innerHTML = '<div class="lc-config-note">暂无插件, 将插件 JAR 放入 /lingConsole/addons/ 并重启。</div>';
            return;
        }
        listEl.innerHTML = addons.map(function (a) {
            const stateBadge = a.state === "ENABLED"
                ? '<span class="lc-badge lc-badge--online">运行中</span>'
                : a.state === "ERROR"
                    ? '<span class="lc-badge lc-badge--offline">异常</span>'
                    : '<span class="lc-badge lc-badge--warn">' + (STATE_TEXT[a.state] || a.state) + '</span>';
            const errorLine = a.error ? '<div class="lc-form__error">' + escapeHtml(a.error) + '</div>' : '';
            const configBtn = a.state !== "ERROR" && (a.config || []).length
                ? '<button class="lc-btn lc-btn--sm" data-action="config" data-name="' + escapeAttr(a.name) + '">配置</button> '
                : '';
            return '<div class="lc-addon-card">' +
                '<div class="lc-addon-card__header">' +
                    '<div class="lc-addon-card__title"><strong>' + escapeHtml(a.name) + '</strong> <span class="lc-muted">v' + escapeHtml(a.version || "--") + '</span></div>' +
                    stateBadge +
                '</div>' +
                '<div class="lc-addon-card__meta">' +
                    (a.author ? '作者: ' + escapeHtml(a.author) + ' @ ' : '') +
                    (a.jar ? ' ' + escapeHtml(a.jar) : '') +
                    (a.description ? '<div class="lc-muted">' + escapeHtml(a.description) + '</div>' : '') +
                '</div>' +
                errorLine +
                '<div class="lc-addon-card__actions">' +
                    configBtn +
                    '<button class="lc-btn lc-btn--sm" data-action="reload" data-name="' + escapeAttr(a.name) + '">热重载</button>' +
                '</div>' +
                '</div>';
        }).join("");

        listEl.querySelectorAll("button[data-action]").forEach(function (btn) {
            btn.addEventListener("click", function () {
                const action = btn.getAttribute("data-action");
                const name = btn.getAttribute("data-name");
                if (action === "config") openConfig(name);
                else if (action === "reload") reloadAddon(name);
            });
        });
    }


    let configSchema = [];

    function openConfig(name) {
        API.get("/addons").then(function (addons) {
            const a = addons.find(function (x) { return x.name === name; });
            if (!a) return;
            editingName = name;
            configSchema = a.config || [];
            document.getElementById("config-modal-title").textContent = "插件配置 - " + a.name;
            document.getElementById("config-error").textContent = "";
            renderConfigForm(configSchema);
            document.getElementById("config-modal").style.display = "flex";
        }).catch(function (e) { LC.dialog.alert(e.message); });
    }

    function renderConfigForm(schema) {
        const form = document.getElementById("config-form");
        if (!schema.length) {
            form.innerHTML = '<div class="lc-config-note">该插件未声明配置项。</div>';
            return;
        }
        form.innerHTML = schema.map(function (c) {
            const desc = c.description ? '<div class="lc-muted">' + escapeHtml(c.description) + '</div>' : '';
            return '<div class="lc-form__group" data-key="' + escapeAttr(c.key) + '">' +
                '<label class="lc-form__label">' + escapeHtml(c.label || c.key) + '</label>' +
                inputFor(c) + desc +
                '</div>';
        }).join("");
    }

    function inputFor(c) {
        const value = c.value != null ? c.value : (c.default != null ? c.default : "");
        switch (c.type) {
            case "TEXT":
                return '<textarea class="lc-input" data-input style="width:100%;min-height:90px;font-family:monospace">' + escapeHtml(value) + '</textarea>';
            case "INT":
                return '<input class="lc-input" data-input type="number" value="' + escapeAttr(value) + '">';
            case "BOOL":
                return '<label class="lc-perm-check"><input data-input type="checkbox"' + (value === "true" ? " checked" : "") + '></label>';
            case "SELECT":
                return '<select class="lc-input" data-input>' +
                    (c.options || []).map(function (o) {
                        return '<option value="' + escapeAttr(o) + '"' + (String(o) === String(value) ? " selected" : "") + '>' + escapeHtml(o) + '</option>';
                    }).join("") + '</select>';
            default:
                return '<input class="lc-input" data-input type="text" value="' + escapeAttr(value) + '">';
        }
    }

    function saveConfig() {
        if (!editingName) return;
        const values = {};
        document.querySelectorAll("#config-form .lc-form__group").forEach(function (g) {
            const key = g.getAttribute("data-key");
            const entry = configSchema.find(function (c) { return c.key === key; });
            const input = g.querySelector("[data-input]");
            if (!entry || !input) return;
            if (entry.type === "BOOL") {
                values[key] = String(input.checked);
            } else {
                values[key] = input.value;
            }
        });
        API.put("/addons/" + editingName + "/config", { values: values })
            .then(function () {
                document.getElementById("config-modal").style.display = "none";
                LC.dialog.alert("配置已保存并热重载");
                load();
            })
            .catch(function (e) {
                document.getElementById("config-error").textContent = e.message || "保存失败";
            });
    }

    async function reloadAddon(name) {
        if (!await LC.dialog.confirm("确认热重载插件 " + name + "?")) return;
        API.post("/addons/" + name + "/reload")
            .then(function () {
                LC.dialog.alert("已热重载");
                load();
            })
            .catch(function (e) { LC.dialog.alert(e.message || "重载失败"); });
    }

    function escapeHtml(s) {
        return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
            return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
        });
    }
    function escapeAttr(s) {
        return escapeHtml(s).replace(/"/g, "&quot;");
    }

    document.getElementById("btn-close-config-modal").addEventListener("click", function () {
        document.getElementById("config-modal").style.display = "none";
    });
    document.getElementById("btn-cancel-config-modal").addEventListener("click", function () {
        document.getElementById("config-modal").style.display = "none";
    });
    document.getElementById("btn-save-config").addEventListener("click", saveConfig);

    window.app.onReady(load);
})();
