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
window.API = (function () {
    const BASE = "/api";

    async function request(method, path, body) {
        const opts = {
            method: method,
            headers: { "Content-Type": "application/json" }
        };
        if (body !== undefined) {
            opts.body = JSON.stringify(body);
        }
        const resp = await fetch(BASE + path, opts);
        let json;
        try {
            json = await resp.json();
        } catch (e) {
            throw new Error("响应解析失败: HTTP " + resp.status);
        }
        if (!resp.ok || (json && json.status !== 200)) {

            if (resp.status === 401 && !window.location.pathname.replace(/\/+$/, "").startsWith("/login")) {
                window.location.href = "/login";
            }
            const msg = json && json.message;
            throw new Error((msg && msg !== "null" && String(msg).trim()) ? String(msg) : "请求失败, 请稍后重试");
        }
        return json.data;
    }

    return {
        get: (path) => request("GET", path),
        post: (path, body) => request("POST", path, body),
        put: (path, body) => request("PUT", path, body),
        del: (path) => request("DELETE", path),
        request: request
    };
})();
