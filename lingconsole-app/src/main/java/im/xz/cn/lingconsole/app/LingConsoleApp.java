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

import im.xz.cn.lingconsole.app.panel.PanelConfig;
import im.xz.cn.lingconsole.app.panel.PanelServer;
import im.xz.cn.lingconsole.app.panel.repository.DatabaseManager;
import im.xz.cn.lingconsole.app.panel.repository.RootAccountRepository;
import im.xz.cn.lingconsole.app.panel.repository.UserRepository;
import im.xz.cn.lingconsole.app.panel.service.UserService;
import im.xz.cn.lingconsole.app.web.WebUiInitializer;
import im.xz.cn.lingconsole.common.config.Constants;
import im.xz.cn.lingconsole.common.config.TomlConfig;
import im.xz.cn.lingconsole.common.util.IdUtil;
import im.xz.cn.lingconsole.daemon.DaemonApp;
import im.xz.cn.lingconsole.daemon.DaemonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;


public class LingConsoleApp {


    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone(Constants.DEFAULT_TIMEZONE));
        Launcher.Params params = Launcher.parse(args);
        System.setProperty("lingconsole.log.dir", params.dataDir + "/logs");
        Logger log = LoggerFactory.getLogger(LingConsoleApp.class);
        DirectoryInitializer.init(params.dataDir);
        PanelConfig panelConfig = loadPanelConfig(params.dataDir);
        DaemonConfig daemonConfig = loadDaemonConfig(params.dataDir);

        DatabaseManager db = new DatabaseManager(panelConfig.dbPath());
        String generatedPassword = firstLaunchInit(db);
        writeFirstLaunchPassword(params.dataDir, generatedPassword);

        im.xz.cn.lingconsole.app.util.Treasure.init(db, Path.of(params.dataDir));
im.xz.cn.lingconsole.app.util.CopyrightService.init(db);
        im.xz.cn.lingconsole.common.addon.AddonSocketRegistry socketRegistry = new im.xz.cn.lingconsole.common.addon.AddonSocketRegistry();
        im.xz.cn.lingconsole.common.addon.AddonMenuRegistry menuRegistry = new im.xz.cn.lingconsole.common.addon.AddonMenuRegistry();
        im.xz.cn.lingconsole.common.addon.AddonProxyRegistry proxyRegistry = new im.xz.cn.lingconsole.common.addon.AddonProxyRegistry();
        im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher commandDispatcher =
                new im.xz.cn.lingconsole.common.command.ConsoleCommandDispatcher();
        PanelServer panelServer = null;
        DaemonApp daemonApp = null;
        final im.xz.cn.lingconsole.common.addon.AddonManager addonManager;
        im.xz.cn.lingconsole.common.addon.AddonManager mgr = null;
        if (params.panelEnabled()) {
            panelServer = new PanelServer(panelConfig, db, daemonConfig, params.dataDir,
                    params.singleUserMode, socketRegistry, menuRegistry, proxyRegistry);
        }

        if (params.panelEnabled() || params.daemonEnabled()) {
            final im.xz.cn.lingconsole.common.addon.AddonManager manager =
                    new im.xz.cn.lingconsole.common.addon.AddonManager(Path.of(params.dataDir, "addons"));
            mgr = manager;
            if (panelServer != null) {
                panelServer.setAddonManager(manager);
            }
            final PanelServer ps = panelServer;
            Path dataDir = Path.of(params.dataDir);
            im.xz.cn.lingconsole.addon.AddonContextFactory factory = (info, logger) ->
                    new im.xz.cn.lingconsole.app.addon.AddonContextImpl(info, logger,
                            ps == null ? null : ps.nodeService(),
                            ps == null ? null : ps.userService(),
                            ps == null ? null : ps.logService(),
                            panelConfig, daemonConfig, dataDir, socketRegistry, menuRegistry, proxyRegistry, db,
                            commandDispatcher, manager);
            manager.setContextFactory(factory);
            manager.loadAll(factory);
        } else {
            log.info("Panel 与 Daemon 均未启用, 插件系统不加载");
        }
        addonManager = mgr;

        ConsoleCommandSupport.registerBuiltins(commandDispatcher, addonManager);
        
        if (params.daemonEnabled()) {
            daemonApp = new DaemonApp(daemonConfig, addonManager, socketRegistry);
            daemonApp.start();
        } else {
            log.info("--damon false, Daemon 不启动");
        }

        
        if ("0.0.0.0".equals(panelConfig.host()) || "0.0.0.0".equals(daemonConfig.host())) {
            log.warn("服务绑定 0.0.0.0, 将暴露到所有网卡。请确保已启用防火墙/仅内网可达, 并建议使用 TLS (wss/https) 与强密钥。");
        }



        
        if (params.panelEnabled()) {
            if (panelServer != null) {
                panelServer.start();
            }
        }

        
        if (addonManager != null) {
            final PanelServer fps = panelServer;
            final DaemonApp fda = daemonApp;
            addonManager.setOnReloadStart(name -> {
                commandDispatcher.unregisterNamespace(name);
                im.xz.cn.lingconsole.common.permission.PluginPermissionRegistry.unregisterAll(name);
                proxyRegistry.clear(name);
                menuRegistry.clear(name);
                socketRegistry.clear(name);
            });
            addonManager.setOnReloaded(name -> {
                if (fps != null) {
                    im.xz.cn.lingconsole.common.addon.AddonSocketSupport.apply(fps.socketIOServer(), socketRegistry, name,
                            conn -> fps.isPanelSession(conn.sessionId()));
                }
                if (fda != null) {
                    im.xz.cn.lingconsole.common.addon.AddonSocketSupport.apply(fda.socketIOServer(), socketRegistry, name,
                            conn -> fda.isAuthenticated(conn.sessionId()));
                }
            });
            addonManager.enableAll();
        }

        ConsoleCommandSupport.startLoop(commandDispatcher);

        
        printBanner(params, panelConfig, daemonConfig, generatedPassword);

        
        final DaemonApp finalDaemonApp = daemonApp;
        final PanelServer finalPanelServer = panelServer;
        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭 LingConsole ...");
            im.xz.cn.lingconsole.app.util.Treasure.shutdown();
im.xz.cn.lingconsole.app.util.CopyrightService.shutdown();
            if (addonManager != null) {
                addonManager.disableAll();
            }
            if (finalPanelServer != null) {
                finalPanelServer.stop();
            }
            if (finalDaemonApp != null) {
                finalDaemonApp.stop();
            }
            db.close();
            log.info("LingConsole 已关闭");
            latch.countDown();
        }));

        latch.await();
    }

    
    private static String tomlPath(String path) {
        return path.replace("\\", "\\\\");
    }

    private static PanelConfig loadPanelConfig(String dataDir) throws Exception {
        Logger log = LoggerFactory.getLogger(LingConsoleApp.class);
        Path configFile = Path.of(dataDir, "web", "config.toml");
        TomlConfig.ensureDefaultFile(configFile, PanelConfig.defaultToml().replace("/lingConsole", tomlPath(dataDir)));
        PanelConfig config = PanelConfig.load(configFile);
        log.info("Panel 配置已加载: {}", configFile);
        return config;
    }

    private static DaemonConfig loadDaemonConfig(String dataDir) throws Exception {
        Logger log = LoggerFactory.getLogger(LingConsoleApp.class);
        Path configFile = Path.of(dataDir, "damon", "config.toml");
        boolean isNew = !Files.exists(configFile);
        TomlConfig.ensureDefaultFile(configFile, DaemonConfig.defaultToml().replace("/lingConsole", tomlPath(dataDir)));
        DaemonConfig config = DaemonConfig.load(configFile);

        
        String placeholder = "<auto-generated-on-first-start>";
        if (config.key() == null || config.key().isBlank() || placeholder.equals(config.key())) {
            String key = IdUtil.randomKey();
            String content = Files.readString(configFile);
            content = content.replace(placeholder, key);
            Files.writeString(configFile, content);
            config = DaemonConfig.load(configFile);
            log.info("已生成 Daemon Key: {}", configFile);
        } else if (isNew) {
            log.warn("Daemon 配置为新生成但 key 未生成, 请检查: {}", configFile);
        }
        return config;
    }

    private static String firstLaunchInit(DatabaseManager db) {
        Logger log = LoggerFactory.getLogger(LingConsoleApp.class);
        UserService userService = new UserService(new UserRepository(db), new RootAccountRepository(db));
        String password = userService.firstLaunchInit();
        if (password != null) {
            
            log.warn("首次启动: 已创建 root 账户 [{}], 初始密码见控制台横幅, 请立即登录修改!", Constants.DEFAULT_USERNAME);
        }
        return password;
    }

    
    private static void writeFirstLaunchPassword(String dataDir, String password) {
        if (password == null || dataDir == null || dataDir.isBlank()) {
            return;
        }
        try {
            Path file = Paths.get(dataDir, "first-launch-password.txt");
            Files.writeString(file,
                    "账号: " + Constants.DEFAULT_USERNAME + "\n密码: " + password + "\n");
            try {
                Files.setPosixFilePermissions(file,
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignore) {
                
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(LingConsoleApp.class)
                    .warn("写入初始密码文件失败: {}", e.getMessage());
        }
    }

    private static void printBanner(Launcher.Params params, PanelConfig panelConfig,
                                    DaemonConfig daemonConfig, String generatedPassword) {
        System.out.println();
        System.out.println("┌──────────────────────────────────────────────────┐");
        System.out.println("│              LingConsole v" + Constants.VERSION + "                 │");
        System.out.println("└──────────────────────────────────────────────────┘");
        if (params.panelEnabled()) {
            System.out.println("  Panel :  http://" + panelConfig.host() + ":" + panelConfig.port());
        } else {
            System.out.println("  Panel :  未启动 (--webui false)");
        }
        if (params.daemonEnabled()) {
            System.out.println("  Daemon:  ws://" + daemonConfig.host() + ":" + daemonConfig.port());
        } else {
            System.out.println("  Daemon:  未启动 (--damon false)");
        }
        System.out.println("  数据目录: " + params.dataDir);
        if (generatedPassword != null) {
            System.out.println();
            System.out.println("  ┌────────────────────────────────────────┐");
            System.out.println("  │  首次启动, 初始账号                    │");
            System.out.println("  │  账号: " + Constants.DEFAULT_USERNAME + "                        │");
            System.out.println("  │  密码: " + generatedPassword + "                        │");
            System.out.println("  └────────────────────────────────────────┘");
            System.out.println("  ⚠  请立即登录并修改密码!");
            System.out.println("  ⚠  初始密码已写入 " + params.dataDir + "/first-launch-password.txt (安装脚本读取后自动删除)");
        }
        System.out.println();
    }
    
    private static final class DirectoryInitializer {
        static void init(String dataDir) throws Exception {
            Logger log = LoggerFactory.getLogger(LingConsoleApp.class);
            String[] dirs = {
                    dataDir,
                    dataDir + "/web",
                    dataDir + "/web/data",
                    dataDir + "/web/static",
                    dataDir + "/web/templates",
                    dataDir + "/damon",
                    dataDir + "/damon/data",
                    dataDir + "/apps",
                    dataDir + "/addons",
                    dataDir + "/logs",
            };
            for (String dir : dirs) {
                Files.createDirectories(Paths.get(dir));
            }
            
            WebUiInitializer.deploy(Path.of(dataDir, "web"));
            log.info("数据目录已初始化: {}", dataDir);
        }
    }
}
