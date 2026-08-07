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
import im.xz.cn.lingconsole.app.panel.PanelConfig;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;


public class AuthController {

    private final UserService userService;
    private final SessionService sessionService;
    private final LogService logService;
    private final LoginAttemptService loginAttemptService;
    private final PanelConfig panelConfig;
    private final boolean singleUserMode;
    private final Semaphore loginPermits;

    public AuthController(UserService userService, SessionService sessionService,
                          LogService logService, LoginAttemptService loginAttemptService,
                          PanelConfig panelConfig, boolean singleUserMode) {
        this.userService = userService;
        this.sessionService = sessionService;
        this.logService = logService;
        this.loginAttemptService = loginAttemptService;
        this.panelConfig = panelConfig;
        this.singleUserMode = singleUserMode;
        this.loginPermits = new Semaphore(panelConfig.loginMaxConcurrent(), true);
        userService.configurePasswordVerification(panelConfig.passwordVerificationConcurrency(),
                panelConfig.passwordVerificationTimeoutMillis());
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
        String ip = ctx.ip();
        if (loginAttemptService.isIpRateExceeded(ip)) {
            ctx.status(429);
            ctx.json(ApiResponse.error(429, "登录请求过于频繁, 请稍后重试"));
            return;
        }
        if (contentLengthExceedsLimit(ctx, panelConfig.loginBodyMaxBytes())) {
            reject(ctx, 413, "登录请求正文过大");
            return;
        }
        if (!loginPermits.tryAcquire()) {
            reject(ctx, 429, "登录服务繁忙, 请稍后重试");
            return;
        }
        try {
            LoginRequest req = readLoginRequest(ctx);
            if (req == null) {
                return;
            }
            if (req.username() == null || req.username().length() > 64
                    || req.password() == null || req.password().length() > 1024) {
                reject(ctx, 400, "用户名或密码长度不合法");
                return;
            }
            if (userService.isDatabaseTampered()) {
                reject(ctx, 403, TAMPERED_MESSAGE);
                return;
            }
            if (loginAttemptService.isLocked(ip, req.username())) {
                long remaining = loginAttemptService.remainingSeconds(ip, req.username());
                reject(ctx, 429, "尝试次数过多, 请 " + remaining + " 秒后重试");
                return;
            }

            User user;
            try {
                user = userService.login(req.username(), req.password());
            } catch (UserService.PasswordVerificationBusyException e) {
                reject(ctx, 503, "密码验证服务繁忙, 请稍后重试");
                return;
            }
            if (user == null) {
                loginAttemptService.recordFailure(ip, req.username());
                reject(ctx, 401, "用户名或密码错误");
                return;
            }
            loginAttemptService.reset(ip, req.username());

            String token = sessionService.createSession(user.getId(), panelConfig.sessionTimeout());
            io.javalin.http.Cookie cookie = new io.javalin.http.Cookie(
                    AuthMiddleware.COOKIE_NAME, token, "/", panelConfig.sessionTimeout(),
                    ctx.scheme().equalsIgnoreCase("https"), true);
            cookie.setSameSite(io.javalin.http.SameSite.STRICT);
            ctx.cookie(cookie);
            logService.record(user.getId(), "auth.login", user.getUsername(), "用户登录", ip);
            ctx.json(ApiResponse.ok(Map.of("user", safeUser(user))));
        } finally {
            loginPermits.release();
        }
    }

    private LoginRequest readLoginRequest(Context ctx) {
        try (InputStream in = ctx.bodyInputStream()) {
            byte[] body = in.readNBytes(panelConfig.loginBodyMaxBytes() + 1);
            if (body.length > panelConfig.loginBodyMaxBytes()) {
                reject(ctx, 413, "登录请求正文过大");
                return null;
            }
            return ApiResponse.mapper().readValue(body, LoginRequest.class);
        } catch (Exception e) {
            reject(ctx, 400, "登录请求格式不合法");
            return null;
        }
    }

    private static boolean contentLengthExceedsLimit(Context ctx, int limit) {
        String value = ctx.header("Content-Length");
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) > limit;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private static void reject(Context ctx, int status, String message) {
        ctx.status(status);
        ctx.json(ApiResponse.error(status, message));
    }

    
    private void logout(Context ctx) {
        String token = ctx.attribute(AuthMiddleware.ATTR_TOKEN);
        if (token != null) {
            sessionService.logout(token);
        }
        ctx.removeCookie(AuthMiddleware.COOKIE_NAME, "/");
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
        ChangePasswordRequest req;
        try {
            req = ctx.bodyAsClass(ChangePasswordRequest.class);
        } catch (RuntimeException e) {
            throw im.xz.cn.lingconsole.app.panel.exception.ApiException.badRequest("请求正文格式不合法");
        }
        if (req == null || req.oldPassword() == null || req.newPassword() == null) {
            throw im.xz.cn.lingconsole.app.panel.exception.ApiException.badRequest("缺少 oldPassword 或 newPassword");
        }
        String policyErr = im.xz.cn.lingconsole.common.util.PasswordPolicy.validate(
                req.newPassword(), req.oldPassword(), user.getUsername());
        if (policyErr != null) {
            ctx.status(400);
            ctx.json(ApiResponse.error(400, policyErr));
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
        if (user.getRole() == im.xz.cn.lingconsole.common.model.UserRole.ROOT) {
            try {
                Files.deleteIfExists(Path.of(panelConfig.firstLaunchPasswordFile()));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(AuthController.class)
                        .warn("root 密码已修改, 但删除首次密码文件失败: {}", e.getMessage());
            }
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
