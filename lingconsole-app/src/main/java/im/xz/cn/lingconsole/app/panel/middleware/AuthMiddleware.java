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
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.service.PermissionService;
import im.xz.cn.lingconsole.app.panel.service.SessionService;
import io.javalin.http.Context;


public class AuthMiddleware {

    public static final String COOKIE_NAME = "ling_session";
    public static final String ATTR_USER = "currentUser";
    public static final String ATTR_TOKEN = "currentToken";
    public static final String ATTR_AUTH_USER = "authUser";

    private final SessionService sessionService;
    private final PermissionService permissionService;

    public AuthMiddleware(SessionService sessionService, PermissionService permissionService) {
        this.sessionService = sessionService;
        this.permissionService = permissionService;
    }

    public void handle(Context ctx) {
        String token = ctx.cookie(COOKIE_NAME);
        if (token == null || token.isBlank()) {
            token = ctx.header("X-LingConsole-Token");
        }
        User user;
        AuthUser auth;
        try {
            user = sessionService.validateToken(token);
            auth = user == null ? null : permissionService.buildAuthUser(user);
        } catch (RuntimeException e) {
            throw ApiException.unauthorized("会话或权限状态无法验证");
        }
        if (user == null) {
            throw ApiException.unauthorized("未登录或会话已过期");
        }
        ctx.attribute(ATTR_USER, user);
        ctx.attribute(ATTR_TOKEN, token);
        ctx.attribute(ATTR_AUTH_USER, auth);
    }

    public static User currentUser(Context ctx) {
        return ctx.attribute(ATTR_USER);
    }

    public static AuthUser authUser(Context ctx) {
        return ctx.attribute(ATTR_AUTH_USER);
    }
}
