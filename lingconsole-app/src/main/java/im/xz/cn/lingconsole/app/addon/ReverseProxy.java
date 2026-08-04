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
package im.xz.cn.lingconsole.app.addon;

import im.xz.cn.lingconsole.common.addon.AddonProxy;
import io.javalin.http.Context;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;


public final class ReverseProxy {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length");

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private ReverseProxy() {
    }

    public static void forward(Context ctx, AddonProxy cfg) {
        String prefix = "/api/addon/" + cfg.addonName() + cfg.mountPath();
        String path = ctx.path();
        String rest = path.length() > prefix.length() ? path.substring(prefix.length()) : "";
        String query = ctx.queryString();
        String url = cfg.scheme() + "://" + cfg.host() + ":" + cfg.port()
                + cfg.basePath() + rest + (query != null && !query.isEmpty() ? "?" + query : "");
        try {
            String method = ctx.method().name();
            byte[] body = readBody(ctx);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60));
            if ("GET".equals(method)) {
                b.GET();
            } else {
                b.method(method, body.length == 0
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofByteArray(body));
            }
            ctx.headerMap().forEach((k, v) -> {
                if (!HOP_BY_HOP.contains(k.toLowerCase())) {
                    b.header(k, v);
                }
            });
            b.header("X-Forwarded-For", ctx.ip());
            b.header("X-Forwarded-Proto", ctx.scheme());
            b.header("X-Forwarded-Host", ctx.host());

            HttpResponse<byte[]> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            resp.headers().map().forEach((k, vs) -> {
                if (HOP_BY_HOP.contains(k.toLowerCase())) {
                    return;
                }
                for (String v : vs) {
                    ctx.header(k, v);
                }
            });
            ctx.status(resp.statusCode());
            ctx.result(resp.body());
        } catch (Exception e) {
            ctx.status(502);
            ctx.contentType("text/plain; charset=utf-8");
            ctx.result("Bad Gateway: " + e.getMessage());
        }
    }

    private static byte[] readBody(Context ctx) {
        try {
            return ctx.bodyAsBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
