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
package im.xz.cn.lingconsole.common.addon;

import im.xz.cn.lingconsole.addon.AddonContext;
import im.xz.cn.lingconsole.addon.AddonInfo;
import im.xz.cn.lingconsole.addon.AddonLogger;
import im.xz.cn.lingconsole.addon.AddonSocketHandler;
import im.xz.cn.lingconsole.addon.AddonState;
import im.xz.cn.lingconsole.addon.CommandHandler;
import im.xz.cn.lingconsole.addon.service.AppService;
import im.xz.cn.lingconsole.addon.service.ConfigService;
import im.xz.cn.lingconsole.addon.service.FileService;
import im.xz.cn.lingconsole.addon.service.LogService;
import im.xz.cn.lingconsole.addon.service.MonitorService;
import im.xz.cn.lingconsole.addon.service.NodeService;
import im.xz.cn.lingconsole.addon.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonManagerTest {

    @TempDir
    Path dataDir;

    private static final String TEST_ADDON_SOURCE = """
            package testaddon;
            import im.xz.cn.lingconsole.addon.*;
            import java.nio.file.*;
            public class TestAddon implements Addon {
                private Path dir;
                public void onLoad(AddonContext ctx) { dir = ctx.addonDataDir(); write("load"); }
                public void onEnable(AddonContext ctx) { write("enable"); }
                public void onDisable() { write("disable"); }
                private void write(String s) {
                    try { Files.writeString(dir.resolve("lifecycle.txt"), s + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
                    catch (Exception e) { }
                }
            }
            """;

    private static final String BOOM_SOURCE = """
            package testaddon;
            import im.xz.cn.lingconsole.addon.*;
            public class BoomAddon implements Addon {
                public void onLoad(AddonContext ctx) { throw new RuntimeException("boom-load"); }
            }
            """;

    @Test
    void lifecycleDependencyAndFaultIsolation() throws Exception {
        Path addonsDir = dataDir.resolve("addons");
        Files.createDirectories(addonsDir);

        buildAddon(addonsDir, "base", "testaddon.TestAddon", TEST_ADDON_SOURCE, List.of());
        buildAddon(addonsDir, "dep", "testaddon.TestAddon", TEST_ADDON_SOURCE, List.of("base"));
        buildAddon(addonsDir, "boom", "testaddon.BoomAddon", BOOM_SOURCE, List.of());
        buildAddon(addonsDir, "orphan", "testaddon.TestAddon", TEST_ADDON_SOURCE, List.of("missing-dep"));

        AddonManager manager = new AddonManager(addonsDir);
        List<AddonDescriptor> discovered = manager.discover();

        
        List<String> order = discovered.stream().map(AddonDescriptor::name).toList();
        assertTrue(order.indexOf("base") < order.indexOf("dep"), "依赖应在依赖者之前加载");
        assertFalse(order.contains("orphan"), "缺失依赖的插件应被跳过");

        List<String> loadOrder = new ArrayList<>();
        manager.loadAll((info, logger) -> {
            loadOrder.add(info.name());
            return stubContext(info, dataDir);
        });
        manager.enableAll();

        assertEquals(AddonState.ENABLED, manager.byName("base").state());
        assertEquals(AddonState.ENABLED, manager.byName("dep").state());
        assertEquals(AddonState.ERROR, manager.byName("boom").state());
        assertEquals("boom-load", manager.byName("boom").error());

        
        String baseLifecycle = Files.readString(addonsDir.resolve("base").resolve("lifecycle.txt"));
        assertTrue(baseLifecycle.contains("load"), "应调用 onLoad");
        assertTrue(baseLifecycle.contains("enable"), "应调用 onEnable");
        
        assertTrue(loadOrder.contains("base"));
        assertTrue(loadOrder.contains("dep"));

        manager.disableAll();
        assertTrue(Files.readString(addonsDir.resolve("base").resolve("lifecycle.txt")).contains("disable"),
                "应调用 onDisable");
        assertEquals(AddonState.DISABLED, manager.byName("dep").state());
    }

    @Test
    void markErrorFlipsStateAndEnableAllDoesNotResurrect() throws Exception {
        Path addonsDir = dataDir.resolve("addons-err");
        Files.createDirectories(addonsDir);
        buildAddon(addonsDir, "base", "testaddon.TestAddon", TEST_ADDON_SOURCE, List.of());

        AddonManager manager = new AddonManager(addonsDir);
        manager.loadAll((info, logger) -> stubContext(info, dataDir));
        manager.enableAll();
        assertEquals(AddonState.ENABLED, manager.byName("base").state());

        manager.markError("base", "invalid permission node \"中文节点\"");
        assertEquals(AddonState.ERROR, manager.byName("base").state());
        assertTrue(manager.byName("base").error().contains("invalid permission node"));

        
        manager.enableAll();
        assertEquals(AddonState.ERROR, manager.byName("base").state(),
                "标记 ERR 后 enableAll 不得复活为 ENABLED");

        
        manager.disableAll();
    }

    @Test
    void softDependencyMissingDoesNotSkip() throws Exception {
        Path addonsDir = dataDir.resolve("addons2");
        Files.createDirectories(addonsDir);
        
        buildAddon(addonsDir, "hard-miss", "testaddon.TestAddon", TEST_ADDON_SOURCE, List.of("not-exist"));
        
        buildAddonWithSoftDeps(addonsDir, "soft-miss", "testaddon.TestAddon", TEST_ADDON_SOURCE,
                List.of(), List.of("not-exist-optional"));

        AddonManager manager = new AddonManager(addonsDir);
        List<String> discovered = manager.discover().stream().map(AddonDescriptor::name).toList();
        assertFalse(discovered.contains("hard-miss"), "缺失硬依赖应跳过");
        assertTrue(discovered.contains("soft-miss"), "缺失软依赖不应跳过");
    }

    @Test
    void softDependencyOrdersAfterWhenPresent() throws Exception {
        Path addonsDir = dataDir.resolve("addons3");
        Files.createDirectories(addonsDir);
        buildAddon(addonsDir, "provider", "testaddon.TestAddon", TEST_ADDON_SOURCE, List.of());
        buildAddonWithSoftDeps(addonsDir, "consumer", "testaddon.TestAddon", TEST_ADDON_SOURCE,
                List.of(), List.of("provider"));

        AddonManager manager = new AddonManager(addonsDir);
        List<String> discovered = manager.discover().stream().map(AddonDescriptor::name).toList();
        assertTrue(discovered.indexOf("provider") < discovered.indexOf("consumer"),
                "软依赖存在时应在其后加载");
    }

    
    
    

    private AddonContext stubContext(AddonInfo info, Path dataDir) {
        return new AddonContext() {
            private final Path addonDataDir = dataDir.resolve("addons").resolve(info.name());

            @Override public AddonInfo info() { return info; }
            @Override public AddonLogger logger() { return new AddonLogger() {
                @Override public void info(String m, Object... a) { }
                @Override public void warn(String m, Object... a) { }
                @Override public void error(String m, Object... a) { }
                @Override public void debug(String m, Object... a) { }
            }; }
            @Override public NodeService nodes() { return null; }
            @Override public AppService apps() { return null; }
            @Override public FileService files() { return null; }
            @Override public MonitorService monitor() { return null; }
            @Override public im.xz.cn.lingconsole.addon.service.ExecService exec() { return null; }
            @Override public im.xz.cn.lingconsole.addon.service.DataService data() { return null; }
            @Override public UserService users() { return null; }
            @Override public LogService logs() { return null; }
            @Override public ConfigService config() { return null; }
            @Override public void registerPanelRoute(im.xz.cn.lingconsole.addon.AddonRouteMethod m, String p,
                                                     im.xz.cn.lingconsole.addon.AddonRouteHandler h,
                                                     String perm) { }
            @Override public void registerDaemonRoute(im.xz.cn.lingconsole.addon.AddonRouteMethod m, String p,
                                                      im.xz.cn.lingconsole.addon.AddonRouteHandler h) { }
            @Override public void registerSocketEvent(String ns, String e, AddonSocketHandler h) { }
            @Override public void registerPanelMenu(String label, String url) { }
            @Override public void registerCommand(String command, CommandHandler handler) { }
            @Override public void registerPermission(String key, String label) { }
            @Override public void registerPanelProxy(String m, String s, String h, int p, String b,
                                                     String perm) { }
            @Override public ScheduledExecutorService scheduler() { return null; }
            @Override public Path dataDir() { return dataDir; }
            @Override public Path addonDataDir() {
                try {
                    Files.createDirectories(addonDataDir);
                } catch (Exception ignored) { }
                return addonDataDir;
            }
        };
    }

    private void buildAddon(Path addonsDir, String name, String mainClass, String source,
                            List<String> deps) throws Exception {
        buildAddonWithSoftDeps(addonsDir, name, mainClass, source, deps, List.of());
    }

    @Test
    void duplicatePluginNameMarksNamespaceConflict() throws Exception {
        Path addonsDir = Files.createTempDirectory("dup-addons");
        Files.createDirectories(addonsDir);

        Path work = Files.createTempDirectory("dup-build");
        Path src = work.resolve("testaddon/TestAddon.java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, TEST_ADDON_SOURCE, StandardCharsets.UTF_8);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null,
                "-cp", System.getProperty("java.class.path"),
                "-d", work.toString(), src.toString());
        assertEquals(0, rc, "测试插件编译失败");

        String toml = "name = \"dup\"\nversion = \"1.0.0\"\nmain = \"testaddon.TestAddon\"\n";
        for (String jarName : new String[]{"dup.jar", "dup-extra.jar"}) {
            Path jar = addonsDir.resolve(jarName);
            try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
                jos.putNextEntry(new JarEntry("addon.toml"));
                jos.write(toml.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
                try (var stream = Files.walk(work)) {
                    for (Path f : stream.filter(Files::isRegularFile).toList()) {
                        String entry = work.relativize(f).toString().replace('\\', '/');
                        jos.putNextEntry(new JarEntry(entry));
                        jos.write(Files.readAllBytes(f));
                        jos.closeEntry();
                    }
                }
            }
        }

        AddonManager manager = new AddonManager(addonsDir);
        manager.loadAll((info, logger) -> stubContext(info, dataDir));
        assertTrue(manager.namespaceConflict("dup"), "同名插件应产生命名空间冲突");
        assertEquals(2, manager.addons().size(), "加载的 + 被拒绝的都应出现在列表中");
        for (AddonManager.LoadedAddon la : manager.addons()) {
            boolean err = (la.state() != AddonState.LOADED && la.state() != AddonState.ENABLED)
                    || manager.namespaceConflict(la.descriptor().name());
            assertTrue(err, "同名冲突双方都应标记 [ERR]: " + la.descriptor().name());
        }
    }

    private void buildAddonWithSoftDeps(Path addonsDir, String name, String mainClass, String source,
                                        List<String> deps, List<String> softDeps) throws Exception {
        Path work = Files.createTempDirectory("addon-build-" + name);
        Path src = work.resolve(mainClass.replace('.', '/') + ".java");
        Files.createDirectories(src.getParent());
        Files.writeString(src, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null,
                "-cp", System.getProperty("java.class.path"),
                "-d", work.toString(),
                src.toString());
        if (rc != 0) {
            throw new IllegalStateException("测试插件编译失败: " + name);
        }

        StringBuilder desc = new StringBuilder();
        desc.append("name = \"").append(name).append("\"\n");
        desc.append("version = \"1.0.0\"\n");
        desc.append("main = \"").append(mainClass).append("\"\n");
        if (deps != null && !deps.isEmpty()) {
            desc.append("dependencies = [");
            for (String d : deps) {
                desc.append("\"").append(d).append("\", ");
            }
            desc.append("]\n");
        }
        if (softDeps != null && !softDeps.isEmpty()) {
            desc.append("soft-dependencies = [");
            for (String d : softDeps) {
                desc.append("\"").append(d).append("\", ");
            }
            desc.append("]\n");
        }

        Path jar = addonsDir.resolve(name + ".jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
            jos.putNextEntry(new JarEntry("addon.toml"));
            jos.write(desc.toString().getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();

            try (var stream = Files.walk(work)) {
                var files = stream.filter(Files::isRegularFile).toList();
                for (Path f : files) {
                    String entry = work.relativize(f).toString().replace('\\', '/');
                    jos.putNextEntry(new JarEntry(entry));
                    jos.write(Files.readAllBytes(f));
                    jos.closeEntry();
                }
            }
        }
    }
}
