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
package im.xz.cn.lingconsole.app.panel.middleware;

import im.xz.cn.lingconsole.app.panel.exception.ApiException;
import im.xz.cn.lingconsole.app.panel.model.AuthUser;
import io.javalin.http.Context;


public class PermissionMiddleware {

    private PermissionMiddleware() {
    }

    
    public static void requirePermission(Context ctx, String permissionKey) {
        AuthUser auth = AuthMiddleware.authUser(ctx);
        if (auth == null) {
            throw ApiException.unauthorized("未登录");
        }
        if (!auth.hasPermission(permissionKey)) {
            throw ApiException.forbidden("权限不足: 缺少 " + permissionKey);
        }
    }
}
