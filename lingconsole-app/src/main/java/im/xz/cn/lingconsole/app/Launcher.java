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


public final class Launcher {
    private Launcher() {
    }
    public static final class Params {
        public boolean webui = true;
        public boolean damon = true;
        public String dataDir = "/lingConsole";
        
        public boolean singleUserMode = false;

        public boolean panelEnabled() {
            return webui;
        }

        public boolean daemonEnabled() {
            return damon;
        }
    }

    public static Params parse(String[] args) {
        Params params = new Params();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--webui" -> params.webui = parseBool(next(args, i), true);
                case "--damon" -> params.damon = parseBool(next(args, i), true);
                case "--singleUserMode" -> params.singleUserMode = parseBool(next(args, i), true);
                case "--config", "--data-dir" -> params.dataDir = next(args, i);
                case "--version", "-v" -> {
                    System.out.println(im.xz.cn.lingconsole.common.config.Constants.VERSION);
                    System.exit(0);
                }
                case "--help", "-h" -> {
                    printHelp();
                    System.exit(0);
                }
                default -> {
                    if (arg.startsWith("--")) {
                        System.err.println("未知参数: " + arg);
                        printHelp();
                        System.exit(1);
                    }
                }
            }
        }
        return params;
    }

    private static String next(String[] args, int i) {
        if (i + 1 >= args.length) {
            System.err.println("参数缺失: " + args[i]);
            System.exit(1);
        }
        return args[i + 1];
    }

    private static boolean parseBool(String value, boolean def) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return def;
    }

    private static void printHelp() {
        String version = im.xz.cn.lingconsole.common.config.Constants.VERSION;
        System.out.println("LingConsole v" + version + "\n" +
                "\n" +
                "用法:\n" +
                "  java -jar LingConsole.jar                    正常启动\n" +
                "  java -jar LingConsole.jar --webui false      仅启动 Daemon (端口 55700)\n" +
                "  java -jar LingConsole.jar --damon false      仅启动 Panel (端口 55600)\n" +
                "  java -jar LingConsole.jar --config /path     指定数据目录\n" +
                "  java -jar LingConsole.jar --singleUserMode true   开启单用户模式 (仅 ling 登录, 默认 false)\n" +
                "  java -jar LingConsole.jar --help             查看帮助\n");
    }
}
