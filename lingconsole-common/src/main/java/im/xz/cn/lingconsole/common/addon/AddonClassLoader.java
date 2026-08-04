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

import java.net.URL;
import java.net.URLClassLoader;


public class AddonClassLoader extends URLClassLoader {

    private static final String[] PARENT_FIRST_PREFIXES = {
            "im.xz.cn.lingconsole.addon.",   
            "im.xz.cn.lingconsole.common.",
            "org.slf4j.",
            "io.javalin.",
            "java.",
            "javax.",
            "jakarta.",
            "jdk.",
            "sun.",
            "com.fasterxml.",
            "org.tomlj.",
            "com.zaxxer.",
            "org.sqlite.",
    };

    private final String addonName;

    public AddonClassLoader(String addonName, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.addonName = addonName;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                if (isParentFirst(name)) {
                    c = tryParent(name);
                }
                if (c == null) {
                    c = tryFind(name);
                }
                if (c == null) {
                    c = getParent().loadClass(name);
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    private Class<?> tryParent(String name) {
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private Class<?> tryFind(String name) {
        try {
            return findClass(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private boolean isParentFirst(String name) {
        for (String prefix : PARENT_FIRST_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public String addonName() {
        return addonName;
    }
}
