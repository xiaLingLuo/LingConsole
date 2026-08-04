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
    const form = document.getElementById("login-form");
    const errorEl = document.getElementById("login-error");
    const btn = document.getElementById("login-btn");

    if (form) {
        form.addEventListener("submit", async function (e) {
            e.preventDefault();
            const username = document.getElementById("login-username").value.trim();
            const password = document.getElementById("login-password").value;
            if (!username || !password) {
                errorEl.textContent = "请输入账号和密码";
                return;
            }
            btn.disabled = true;
            errorEl.textContent = "";
            try {
                await API.post("/auth/login", { username: username, password: password });
                window.location.href = "/dashboard";
            } catch (err) {
                errorEl.textContent = err.message || "登录失败";
            } finally {
                btn.disabled = false;
            }
        });
    }
})();
