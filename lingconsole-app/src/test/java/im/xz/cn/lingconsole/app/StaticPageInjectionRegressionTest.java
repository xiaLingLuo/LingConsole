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
package im.xz.cn.lingconsole.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticPageInjectionRegressionTest {

    @Test
    void fileBreadcrumbUsesDomApiForDynamicPaths() throws IOException {
        String source = resource("/static/js/pages/page-files.js");
        String renderPage = function(source, "renderPage", "clearSelection");
        String renderBreadcrumb = function(source, "renderBreadcrumb", "appendCrumb");
        String appendCrumb = function(source, "appendCrumb", "bindCrumbs");

        assertTrue(renderPage.contains("Number.isFinite(Number(e.size))"));
        assertTrue(renderPage.contains("escapeHtml(window.app.formatTime(e.modified))"));
        assertFalse(renderBreadcrumb.contains("innerHTML"));
        assertTrue(renderBreadcrumb.contains("appendCrumb(el, acc, p"));
        assertTrue(appendCrumb.contains("crumb.dataset.path = path"));
        assertTrue(appendCrumb.contains("crumb.textContent = label"));
    }

    @Test
    void monitorNodeOptionsAndDiskTooltipEscapeDynamicValues() throws IOException {
        String source = resource("/static/js/pages/page-monitor.js");
        String init = function(source, "init", "initCharts");
        String initCharts = function(source, "initCharts", "pushSeries");

        assertFalse(init.contains("innerHTML"));
        assertTrue(init.contains("option.textContent = n.name"));
        assertTrue(initCharts.contains("escapeHtml(d.diskInfo.mount)"));
        assertTrue(initCharts.contains("escapeHtml(used)"));
        assertTrue(initCharts.contains("escapeHtml(total)"));
        assertTrue(initCharts.contains("escapeHtml(free)"));
        assertTrue(initCharts.contains("escapeHtml(params.name)"));
        assertTrue(initCharts.contains("escapeHtml(params.percent)"));
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = StaticPageInjectionRegressionTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "Missing test resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String function(String source, String name, String nextName) {
        int start = source.indexOf("function " + name + "(");
        int end = source.indexOf("function " + nextName + "(", start);
        assertTrue(start >= 0 && end > start, "Could not locate function " + name);
        return source.substring(start, end);
    }
}
