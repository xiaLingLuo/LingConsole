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

import im.xz.cn.lingconsole.addon.Addon;
import im.xz.cn.lingconsole.addon.AddonContext;
import im.xz.cn.lingconsole.addon.AddonContextFactory;
import im.xz.cn.lingconsole.addon.AddonLogger;
import im.xz.cn.lingconsole.addon.AddonState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


public class AddonManager {

    private static final Logger log = LoggerFactory.getLogger(AddonManager.class);

    private final Path addonsDir;
    
    private final Map<String, LoadedAddon> byName = new LinkedHashMap<>();
    
    private final Map<String, LoadedAddon> rejected = new LinkedHashMap<>();
    
    private final Set<String> conflictedNames = new HashSet<>();
    
    private volatile AddonContextFactory contextFactory;
    
    private volatile java.util.function.Consumer<String> onReloadStart;
    
    private volatile java.util.function.Consumer<String> onReloaded;

    public AddonManager(Path addonsDir) {
        this.addonsDir = addonsDir;
    }

    public void setOnReloadStart(java.util.function.Consumer<String> onReloadStart) {
        this.onReloadStart = onReloadStart;
    }

    public void setOnReloaded(java.util.function.Consumer<String> onReloaded) {
        this.onReloaded = onReloaded;
    }

    public void setContextFactory(AddonContextFactory factory) {
        this.contextFactory = factory;
    }

    
    public List<LoadedAddon> addons() {
        List<LoadedAddon> all = new ArrayList<>(byName.values());
        all.addAll(rejected.values());
        return List.copyOf(all);
    }

    public LoadedAddon byName(String name) {
        return byName.get(name);
    }

    
    public void markError(String name, String reason) {
        LoadedAddon la = byName.get(name);
        if (la != null) {
            la.state = AddonState.ERROR;
            la.error = reason;
            log.error("插件 [{}] 已被标记为 ERR: {}", name, reason);
        }
    }

    
    public boolean namespaceConflict(String name) {
        return name != null && conflictedNames.contains(name);
    }

    public Set<String> conflictedNames() {
        return Set.copyOf(conflictedNames);
    }

    
    public im.xz.cn.lingconsole.addon.AddonContext contextOf(String name) {
        LoadedAddon la = byName.get(name);
        return la == null ? null : la.context();
    }

    
    public List<AddonDescriptor> discover() {
        try {
            Files.createDirectories(addonsDir);
        } catch (IOException e) {
            log.warn("创建插件目录失败: {}", addonsDir, e);
        }
        Map<String, AddonDescriptor> descriptors = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.list(addonsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted()
                    .forEach(jar -> {
                        try {
                            AddonDescriptor d = AddonDescriptorLoader.load(jar);
                            if (descriptors.containsKey(d.name())) {
                                log.warn("插件名重复, 命名空间冲突, 双方均标记 [ERR]: {} ({})", d.name(), jar.getFileName());
                                conflictedNames.add(d.name());
                                rejected.putIfAbsent(d.name(), new LoadedAddon(
                                        d, jar, null, null, null, null, AddonState.ERROR,
                                        "命名空间冲突: 插件名 [" + d.name() + "] 重复"));
                                return;
                            }
                            descriptors.put(d.name(), d);
                            log.info("发现插件: {} v{} ({})", d.name(), d.version(), jar.getFileName());
                        } catch (Exception e) {
                            log.warn("跳过无效插件 {}: {}", jar.getFileName(), e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("扫描插件目录失败: {}", addonsDir, e);
        }
        return topoSort(descriptors);
    }

    
    public void loadAll(AddonContextFactory factory) {
        this.contextFactory = factory;
        for (AddonDescriptor desc : discover()) {
            load(desc);
        }
    }

    private void load(AddonDescriptor desc) {
        Path jar = findJar(desc.name());
        AddonClassLoader cl = null;
        try {
            cl = new AddonClassLoader(
                    desc.name(),
                    new URL[]{jar.toUri().toURL()},
                    AddonManager.class.getClassLoader());
            Class<?> mainClass = Class.forName(desc.mainClass(), true, cl);
            if (!Addon.class.isAssignableFrom(mainClass)) {
                throw new IllegalArgumentException("主类未实现 Addon: " + desc.mainClass());
            }
            AddonLogger logger = new Slf4jAddonLogger(desc.name());
            AddonContext context = contextFactory.create(desc.toInfo(), logger);
            Addon addon = (Addon) mainClass.getDeclaredConstructor().newInstance();

            
            LoadedAddon loaded = new LoadedAddon(desc, jar, cl, addon, context, logger, AddonState.LOADED, null);
            byName.put(desc.name(), loaded);
            try {
                addon.onLoad(context);
            } catch (Throwable e) {
                loaded.state = AddonState.ERROR;
                loaded.error = String.valueOf(e.getMessage());
                log.error("插件 [{}] 加载失败: {}", desc.name(), e.getMessage(), e);
                try {
                    cl.close();
                } catch (IOException ignore) {

                }
                return;
            }
            log.info("插件 [{}] 已加载", desc.name());
        } catch (Throwable e) {
            log.error("插件 [{}] 加载失败: {}", desc.name(), e.getMessage(), e);
            if (cl != null) {
                try {
                    cl.close();
                } catch (IOException ignore) {
                    
                }
            }
            LoadedAddon failed = new LoadedAddon(desc, jar, null, null, null, null, AddonState.ERROR,
                    String.valueOf(e.getMessage()));
            byName.put(desc.name(), failed);
        }
    }

    
    public boolean reload(String name) {
        LoadedAddon old = byName.remove(name);
        if (old == null) {
            return false;
        }
        log.info("插件 [{}] 热重载中...", name);
        java.util.function.Consumer<String> clear = onReloadStart;
        if (clear != null) {
            try {
                clear.accept(name);
            } catch (Exception e) {
                log.debug("插件 [{}] 重载清理回调异常", name, e);
            }
        }
        if (old.instance() != null) {
            try {
                old.instance().onDisable();
            } catch (Throwable e) {
                log.error("插件 [{}] onDisable 异常: {}", name, e.getMessage(), e);
            }
        }
        AddonClassLoader loader = old.classLoader();
        if (loader != null) {
            try {
                loader.close();
            } catch (IOException e) {
                log.debug("插件 [{}] 类加载器关闭失败", name, e);
            }
        }
        
        load(old.descriptor());
        LoadedAddon loaded = byName.get(name);
        boolean ok = loaded != null && loaded.state() == AddonState.LOADED;
        if (ok) {
            try {
                loaded.instance().onEnable(loaded.context());
                if (loaded.state == AddonState.LOADED) {
                    loaded.state = AddonState.ENABLED;
                }
                log.info("插件 [{}] 热重载完成", name);
            } catch (Throwable e) {
                loaded.state = AddonState.ERROR;
                loaded.error = String.valueOf(e.getMessage());
                log.error("插件 [{}] 热重载启用失败: {}", name, e.getMessage(), e);
                ok = false;
            }
        }
        
        java.util.function.Consumer<String> cb = onReloaded;
        if (cb != null) {
            try {
                cb.accept(name);
            } catch (Exception e) {
                log.debug("插件 [{}] 重载回调异常", name, e);
            }
        }
        return ok;
    }

    
    public void enableAll() {
        for (LoadedAddon la : byName.values()) {
            if (la.state() != AddonState.LOADED || la.instance() == null) {
                continue;
            }
            try {
                la.instance().onEnable(la.context());
                if (la.state == AddonState.LOADED) {
                    la.state = AddonState.ENABLED;
                }
                log.info("插件 [{}] 已启用", la.descriptor().name());
            } catch (Throwable e) {
                la.state = AddonState.ERROR;
                la.error = String.valueOf(e.getMessage());
                log.error("插件 [{}] 启用失败: {}", la.descriptor().name(), e.getMessage(), e);
            }
        }
    }

    
    public void disableAll() {
        var values = new ArrayList<>(byName.values());
        for (int i = values.size() - 1; i >= 0; i--) {
            LoadedAddon la = values.get(i);
            if (la.instance() == null) {
                continue;
            }
            try {
                la.instance().onDisable();
                la.state = AddonState.DISABLED;
            } catch (Throwable e) {
                la.state = AddonState.ERROR;
                la.error = String.valueOf(e.getMessage());
                log.error("插件 [{}] 停用失败: {}", la.descriptor().name(), e.getMessage(), e);
            } finally {
                AddonClassLoader loader = la.classLoader();
                if (loader != null) {
                    try {
                        loader.close();
                    } catch (IOException e) {
                        log.debug("插件 [{}] 类加载器关闭失败", la.descriptor().name(), e);
                    }
                }
            }
        }
        log.info("全部插件已停用");
    }

    
    
    

    private List<AddonDescriptor> topoSort(Map<String, AddonDescriptor> descriptors) {
        
        Map<String, AddonDescriptor> valid = new LinkedHashMap<>();
        for (AddonDescriptor d : descriptors.values()) {
            boolean missing = false;
            for (String dep : d.dependencies()) {
                if (dep != null && !dep.isBlank() && !descriptors.containsKey(dep)) {
                    log.warn("插件 [{}] 硬依赖缺失: {} (跳过)", d.name(), dep);
                    missing = true;
                    break;
                }
            }
            if (!missing) {
                valid.put(d.name(), d);
            }
        }

        List<AddonDescriptor> result = new ArrayList<>();
        
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, Integer> hardIndegree = new HashMap<>();
        Map<String, List<String>> graph = new HashMap<>();
        for (AddonDescriptor d : valid.values()) {
            indegree.putIfAbsent(d.name(), 0);
            hardIndegree.putIfAbsent(d.name(), 0);
            for (String dep : d.dependencies()) {
                if (dep == null || dep.isBlank()) {
                    continue;
                }
                if (valid.containsKey(dep)) {
                    graph.computeIfAbsent(dep, k -> new ArrayList<>()).add(d.name());
                    indegree.merge(d.name(), 1, Integer::sum);
                    hardIndegree.merge(d.name(), 1, Integer::sum);
                }
            }
            for (String dep : d.softDependencies()) {
                if (dep == null || dep.isBlank()) {
                    continue;
                }
                if (valid.containsKey(dep)) {
                    graph.computeIfAbsent(dep, k -> new ArrayList<>()).add(d.name());
                    indegree.merge(d.name(), 1, Integer::sum);
                }
            }
        }
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        for (var e : indegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        int processed = 0;
        while (true) {
            
            boolean progress = false;
            while (!queue.isEmpty()) {
                String name = queue.poll();
                result.add(valid.get(name));
                processed++;
                progress = true;
                for (String next : graph.getOrDefault(name, List.of())) {
                    int d = indegree.merge(next, -1, Integer::sum);
                    if (d == 0) {
                        queue.add(next);
                    }
                }
            }
            if (processed == valid.size()) {
                break;
            }
            
            boolean relaxed = false;
            for (String name : valid.keySet()) {
                if (indegree.getOrDefault(name, 0) > 0 && hardIndegree.getOrDefault(name, 0) == 0) {
                    indegree.put(name, 0);
                    queue.add(name);
                    relaxed = true;
                    break;
                }
            }
            if (!relaxed) {
                
                Set<String> remaining = new HashSet<>(valid.keySet());
                result.forEach(d -> remaining.remove(d.name()));
                for (String name : remaining) {
                    log.warn("插件硬依赖存在环, 跳过: {}", name);
                }
                break;
            }
        }
        return result;
    }

    private Path findJar(String addonName) {
        try (Stream<Path> stream = Files.list(addonsDir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> {
                        try {
                            return addonName.equals(AddonDescriptorLoader.load(p).name());
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("找不到插件 JAR: " + addonName));
        } catch (IOException e) {
            throw new IllegalStateException("扫描插件目录失败: " + addonName, e);
        }
    }

    
    
    

    public static final class LoadedAddon {
        private final AddonDescriptor descriptor;
        private final Path jarPath;
        private final AddonClassLoader classLoader;
        private final Addon instance;
        private final AddonContext context;
        private final AddonLogger logger;
        private volatile AddonState state;
        private volatile String error;

        private LoadedAddon(AddonDescriptor descriptor, Path jarPath, AddonClassLoader classLoader,
                            Addon instance, AddonContext context, AddonLogger logger,
                            AddonState state, String error) {
            this.descriptor = descriptor;
            this.jarPath = jarPath;
            this.classLoader = classLoader;
            this.instance = instance;
            this.context = context;
            this.logger = logger;
            this.state = state;
            this.error = error;
        }

        public AddonDescriptor descriptor() {
            return descriptor;
        }

        public Path jarPath() {
            return jarPath;
        }

        public AddonClassLoader classLoader() {
            return classLoader;
        }

        public Addon instance() {
            return instance;
        }

        public AddonContext context() {
            return context;
        }

        public AddonLogger logger() {
            return logger;
        }

        public AddonState state() {
            return state;
        }

        public String error() {
            return error;
        }
    }
}
