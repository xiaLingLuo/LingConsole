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
package im.xz.cn.lingconsole.app.panel.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import im.xz.cn.lingconsole.app.panel.middleware.AuthMiddleware;
import im.xz.cn.lingconsole.app.panel.model.User;
import im.xz.cn.lingconsole.app.panel.service.LogService;
import im.xz.cn.lingconsole.app.panel.service.LoginAttemptService;
import im.xz.cn.lingconsole.app.panel.service.SessionService;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.common.model.ApiResponse;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.Map;


public class AuthController {

    private final UserService userService;
    private final SessionService sessionService;
    private final LogService logService;
    private final LoginAttemptService loginAttemptService;
    private final boolean singleUserMode;

    public AuthController(UserService userService, SessionService sessionService,
                          LogService logService, LoginAttemptService loginAttemptService,
                          boolean singleUserMode) {
        this.userService = userService;
        this.sessionService = sessionService;
        this.logService = logService;
        this.loginAttemptService = loginAttemptService;
        this.singleUserMode = singleUserMode;
    }

    public void register(RoutesConfig routes, String prefix) {
        routes.post(prefix + "/auth/login", this::login);
        routes.post(prefix + "/auth/logout", this::logout);
        routes.get(prefix + "/auth/me", this::me);
        routes.put(prefix + "/auth/password", this::changePassword);
    }

    private static final String TAMPERED_MESSAGE =
            "程序数据库遭到篡改！系统很可能不再安全，因此LingConsole已禁用任何登录！请立即重建数据库和重新安装本程序！";

    
    private void login(Context ctx) {
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);
        String ip = ctx.ip();

        
        if (req.username() == null || req.username().length() > 64
                || req.password() == null || req.password().length() > 1024) {
            ctx.status(400);
            ctx.json(ApiResponse.error(400, "用户名或密码长度不合法"));
            return;
        }

        
        if (userService.isDatabaseTampered()) {
            ctx.status(403);
            ctx.json(ApiResponse.error(403, TAMPERED_MESSAGE));
            return;
        }

        
        if (loginAttemptService.isIpRateExceeded(ip)) {
            ctx.status(429);
            ctx.json(ApiResponse.error(429, "登录请求过于频繁, 请稍后重试"));
            return;
        }

        
        if (loginAttemptService.isLocked(ip, req.username())) {
            long remaining = loginAttemptService.remainingSeconds(ip, req.username());
            ctx.status(429);
            ctx.json(ApiResponse.error(429, "尝试次数过多, 请 " + remaining + " 秒后重试"));
            return;
        }

        User user = userService.login(req.username(), req.password());
        if (user == null) {
            loginAttemptService.recordFailure(ip, req.username());
            ctx.status(401);
            ctx.json(ApiResponse.error(401, "用户名或密码错误"));
            return;
        }
        loginAttemptService.reset(ip, req.username());

        String token = sessionService.createSession(user.getId(), 3600);
        
        io.javalin.http.Cookie cookie = new io.javalin.http.Cookie(
                AuthMiddleware.COOKIE_NAME, token, "/", 3600,
                ctx.scheme().equalsIgnoreCase("https"), true);
        cookie.setSameSite(io.javalin.http.SameSite.STRICT);
        ctx.cookie(cookie);
        logService.record(user.getId(), "auth.login", user.getUsername(), "用户登录", ip);
        ctx.json(ApiResponse.ok(Map.of(
                "token", token,
                "user", safeUser(user))));
    }

    
    private void logout(Context ctx) {
        String token = ctx.cookie(AuthMiddleware.COOKIE_NAME);
        if (token != null) {
            sessionService.logout(token);
            ctx.removeCookie(AuthMiddleware.COOKIE_NAME, "/");
        }
        ctx.json(ApiResponse.ok());
    }

    
    private void me(Context ctx) {
        if (userService.isDatabaseTampered()) {
            ctx.status(403);
            ctx.json(ApiResponse.error(403, TAMPERED_MESSAGE));
            return;
        }
        User user = AuthMiddleware.currentUser(ctx);
        if (user == null) {
            throw new NotFoundResponse();
        }
        im.xz.cn.lingconsole.app.panel.model.AuthUser auth = AuthMiddleware.authUser(ctx);
        ctx.json(ApiResponse.ok(Map.of(
                "user", safeUser(user),
                "permissions", auth == null ? java.util.List.of() : auth.permissions(),
                "singleUserMode", singleUserMode)));
    }

    
    private void changePassword(Context ctx) {
        User user = AuthMiddleware.currentUser(ctx);
        ChangePasswordRequest req = ctx.bodyAsClass(ChangePasswordRequest.class);
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            ctx.status(400);
            ctx.json(ApiResponse.error(400, "新密码长度至少 6 位"));
            return;
        }
        boolean ok;
        if (user.getRole() == im.xz.cn.lingconsole.common.model.UserRole.ROOT) {
            ok = userService.changeRootPassword(req.oldPassword(), req.newPassword());
        } else {
            ok = userService.changeUserPassword(user.getId(), req.oldPassword(), req.newPassword());
        }
        if (!ok) {
            ctx.status(400);
            ctx.json(ApiResponse.error(400, "原密码错误"));
            return;
        }
        
        sessionService.logoutAllForUser(user.getId());
        ctx.removeCookie(AuthMiddleware.COOKIE_NAME, "/");
        logService.record(user.getId(), "auth.changePassword", user.getUsername(), "修改密码", ctx.ip());
        ctx.json(ApiResponse.ok());
    }

    private Map<String, Object> safeUser(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole().value(),
                "roleName", user.getRole().roleName());
    }

    public record LoginRequest(@JsonProperty("username") String username,
                               @JsonProperty("password") String password) {
    }

    public record ChangePasswordRequest(@JsonProperty("oldPassword") String oldPassword,
                                        @JsonProperty("newPassword") String newPassword) {
    }
}
