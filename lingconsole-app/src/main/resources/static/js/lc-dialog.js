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
window.LC = window.LC || {};

LC.dialog = (function () {
    var root = null;

    function ensure() {
        if (root) return root;
        root = document.createElement("div");
        root.className = "lc-dialog-root is-hidden";
        root.innerHTML =
            '<div class="lc-dialog-mask"></div>' +
            '<div class="lc-dialog">' +
            '  <div class="lc-dialog__header">' +
            '    <h3 class="lc-dialog__title"></h3>' +
            '    <button class="lc-dialog__close" type="button">&times;</button>' +
            '  </div>' +
            '  <div class="lc-dialog__body"></div>' +
            '  <div class="lc-dialog__footer"></div>' +
            '</div>';
        document.body.appendChild(root);
        return root;
    }

    function show() {
        var el = ensure();
        el.classList.remove("is-hidden");
    }

    function hide() {
        if (root) root.classList.add("is-hidden");
    }

    function bindClose(onClose) {
        var el = ensure();
        el.querySelector(".lc-dialog__close").onclick = function () { hide(); onClose(); };
        el.querySelector(".lc-dialog-mask").onclick = function () { hide(); onClose(); };
    }

    function alert(message, title) {
        return new Promise(function (resolve) {
            var el = ensure();
            el.querySelector(".lc-dialog__title").textContent = title || "提示";
            el.querySelector(".lc-dialog__body").innerHTML =
                '<div class="lc-dialog__text"></div>';
            el.querySelector(".lc-dialog__text").textContent = message;
            el.querySelector(".lc-dialog__footer").innerHTML =
                '<button class="lc-btn lc-btn--primary" type="button" data-ok="1">确定</button>';
            el.querySelector(".lc-dialog__footer [data-ok]").onclick = function () {
                hide();
                resolve();
            };
            bindClose(function () { resolve(); });
            show();
            var btn = el.querySelector(".lc-dialog__footer [data-ok]");
            if (btn) btn.focus();
        });
    }

    function confirm(message, title) {
        return new Promise(function (resolve) {
            var el = ensure();
            el.querySelector(".lc-dialog__title").textContent = title || "确认";
            el.querySelector(".lc-dialog__body").innerHTML =
                '<div class="lc-dialog__text"></div>';
            el.querySelector(".lc-dialog__text").textContent = message;
            el.querySelector(".lc-dialog__footer").innerHTML =
                '<button class="lc-btn" type="button" data-cancel="1">取消</button>' +
                '<button class="lc-btn lc-btn--primary" type="button" data-ok="1">确定</button>';
            el.querySelector(".lc-dialog__footer [data-cancel]").onclick = function () {
                hide();
                resolve(false);
            };
            el.querySelector(".lc-dialog__footer [data-ok]").onclick = function () {
                hide();
                resolve(true);
            };
            bindClose(function () { resolve(false); });
            show();
            var btn = el.querySelector(".lc-dialog__footer [data-ok]");
            if (btn) btn.focus();
        });
    }

    function prompt(label, defaultValue, title) {
        return new Promise(function (resolve) {
            var el = ensure();
            el.querySelector(".lc-dialog__title").textContent = title || "输入";
            el.querySelector(".lc-dialog__body").innerHTML =
                '<label class="lc-dialog__label"></label>' +
                '<input class="lc-input lc-dialog__input" type="text">';
            el.querySelector(".lc-dialog__label").textContent = label || "";
            var input = el.querySelector(".lc-dialog__input");
            input.value = defaultValue != null ? String(defaultValue) : "";
            el.querySelector(".lc-dialog__footer").innerHTML =
                '<button class="lc-btn" type="button" data-cancel="1">取消</button>' +
                '<button class="lc-btn lc-btn--primary" type="button" data-ok="1">确定</button>';
            function ok() {
                hide();
                resolve(input.value);
            }
            function cancel() {
                hide();
                resolve(null);
            }
            el.querySelector(".lc-dialog__footer [data-cancel]").onclick = cancel;
            el.querySelector(".lc-dialog__footer [data-ok]").onclick = ok;
            input.onkeydown = function (e) {
                if (e.key === "Enter") ok();
                if (e.key === "Escape") cancel();
            };
            bindClose(cancel);
            show();
            input.focus();
            input.select();
        });
    }

    function toast(message, type) {
        var t = document.createElement("div");
        t.className = "lc-toast lc-toast--" + (type === "error" || type === "success" ? type : "info");
        t.textContent = message;
        document.body.appendChild(t);
        setTimeout(function () {
            t.classList.add("is-out");
            setTimeout(function () { t.remove(); }, 300);
        }, 2600);
    }

    return {
        alert: alert,
        confirm: confirm,
        prompt: prompt,
        toast: toast
    };
})();
