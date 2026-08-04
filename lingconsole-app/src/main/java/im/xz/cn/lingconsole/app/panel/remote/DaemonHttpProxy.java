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
package im.xz.cn.lingconsole.app.panel.remote;

import im.xz.cn.lingconsole.app.panel.model.Node;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;


public class DaemonHttpProxy {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public String baseUrl(Node node) {
        String url = node.getUrl();
        String rest = url.substring(url.indexOf("://") + 3);
        String scheme = url.startsWith("wss://") ? "https" : "http";
        return scheme + "://" + rest + "/consoleapi";
    }

    private HttpRequest.Builder builder(Node node, String path, Map<String, String> query) {
        StringBuilder url = new StringBuilder(baseUrl(node)).append(path);
        if (query != null && !query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String, String> e : query.entrySet()) {
                if (!first) {
                    url.append('&');
                }
                url.append(e.getKey()).append('=').append(java.net.URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }
        return HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .header("X-LingConsole-Key", node.getKey());
    }

    public HttpResponse<String> get(Node node, String path, Map<String, String> query) throws IOException, InterruptedException {
        return client.send(builder(node, path, query).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> postJson(Node node, String path, Map<String, String> query, String jsonBody)
            throws IOException, InterruptedException {
        return client.send(builder(node, path, query)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> delete(Node node, String path, Map<String, String> query)
            throws IOException, InterruptedException {
        return client.send(builder(node, path, query).DELETE().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    
    public HttpResponse<String> uploadRaw(Node node, String daemonPath, String path, String filename, InputStream body)
            throws IOException, InterruptedException {
        Map<String, String> query = java.util.Map.of("path", path, "filename", filename);
        return client.send(builder(node, daemonPath, query)
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    
    public HttpResponse<InputStream> download(Node node, String daemonPath, String path) throws IOException, InterruptedException {
        return client.send(builder(node, daemonPath, Map.of("path", path)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }
}
