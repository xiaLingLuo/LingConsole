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
package im.xz.cn.lingconsole.app.web;

import im.xz.cn.lingconsole.app.panel.PanelServer;
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;

import java.util.Map;


public class PageController {

    private final ThymeleafRenderer renderer;
    private final boolean singleUserMode;

    public PageController(ThymeleafRenderer renderer, boolean singleUserMode) {
        this.renderer = renderer;
        this.singleUserMode = singleUserMode;
    }

    public void register(RoutesConfig routes) {
        routes.get("/login", this::login);
        routes.get("/", this::dashboard);
        routes.get("/dashboard", this::dashboard);
        routes.get("/nodes", this::nodes);
        routes.get("/apps", this::apps);
        routes.get("/apps/{nodeId}", this::apps);
        routes.get("/terminal/{nodeId}", this::terminal);
        routes.get("/terminal/{nodeId}/{appId}", this::terminal);
        routes.get("/files/{nodeId}", this::files);
        routes.get("/files/app/{nodeId}/{appId}", this::files);
        routes.get("/monitor", this::monitor);
        routes.get("/monitor/{nodeId}", this::monitor);
        routes.get("/packages", this::packages);
        routes.get("/packages/{nodeId}", this::packages);
        routes.get("/settings", this::settings);
        routes.get("/logs", this::logs);
        routes.get("/users", this::users);
        routes.get("/permission-groups", this::permissionGroups);
        routes.get("/addons", this::addons);
    }

    private void login(Context ctx) {
        html(ctx, "login");
    }

    private void dashboard(Context ctx) {
        html(ctx, "dashboard");
    }

    private void nodes(Context ctx) {
        html(ctx, "nodes");
    }

    private void apps(Context ctx) {
        html(ctx, "node-apps");
    }

    private void terminal(Context ctx) {
        html(ctx, "terminal");
    }

    private void files(Context ctx) {
        html(ctx, "files");
    }

    private void monitor(Context ctx) {
        html(ctx, "monitor");
    }

    private void packages(Context ctx) {
        html(ctx, "packages");
    }

    private void users(Context ctx) {
        if (singleUserMode) {
            ctx.redirect("/dashboard");
            throw new PanelServer.PageRedirectException();
        }
        html(ctx, "users");
    }

    private void settings(Context ctx) {
        ctx.contentType("text/html; charset=utf-8");
        ctx.result(renderer.render("settings", Map.of(
                "page", "settings",
                "version", im.xz.cn.lingconsole.common.config.Constants.VERSION,
                "repoUrl", im.xz.cn.lingconsole.common.config.Constants.REPOSITORY_URL,
                "repoName", im.xz.cn.lingconsole.common.config.Constants.REPOSITORY_NAME,
                "copyrightYear", im.xz.cn.lingconsole.app.util.CopyrightService.year())));
    }

    private void logs(Context ctx) {
        html(ctx, "logs");
    }

    private void addons(Context ctx) {
        html(ctx, "addons");
    }

    private void permissionGroups(Context ctx) {
        html(ctx, "permission-groups");
    }

    private void html(Context ctx, String template) {
        ctx.contentType("text/html; charset=utf-8");
        ctx.result(renderer.render(template, Map.of(
                "page", template,
                "copyrightYear", im.xz.cn.lingconsole.app.util.CopyrightService.year())));
    }
}
