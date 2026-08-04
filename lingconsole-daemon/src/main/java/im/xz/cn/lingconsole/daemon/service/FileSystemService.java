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
package im.xz.cn.lingconsole.daemon.service;

import im.xz.cn.lingconsole.common.util.PathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


public class FileSystemService {

    private static final Logger log = LoggerFactory.getLogger(FileSystemService.class);

    private static final long MAX_READ_SIZE = 100 * 1024; 

    
    public record FileEntry(String name, String path, boolean directory, long size, long modified) {
    }

    
    public static Path resolvePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path p = Path.of(path).normalize();
        if (!p.isAbsolute()) {
            String s = p.toString();
            if (s.equals("\\") || path.equals("/") || path.equals("\\")) {
                
                return p.toAbsolutePath().getRoot();
            }
            throw new IllegalArgumentException("路径必须为绝对路径: " + path);
        }
        return p;
    }

    
    public List<FileEntry> list(String path) throws IOException {
        Path dir = resolvePath(path);
        if (!Files.isDirectory(dir)) {
            throw new IOException("不是目录: " + dir);
        }
        List<FileEntry> entries = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.sorted(java.util.Comparator.<Path, Boolean>comparing(Files::isDirectory).reversed()
                            .thenComparing(p -> p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            if (!Files.isReadable(p)) {
                                log.warn("跳过不可访问条目: {}", p);
                                return;
                            }
                            boolean isDir = Files.isDirectory(p);
                            entries.add(new FileEntry(
                                    p.getFileName().toString(),
                                    p.toAbsolutePath().toString(),
                                    isDir,
                                    isDir ? 0 : Files.size(p),
                                    Files.getLastModifiedTime(p).toMillis() / 1000));
                        } catch (IOException e) {
                            log.warn("读取文件属性失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            throw new IOException("无法读取目录: " + dir + " (" + e.getMessage() + ")", e);
        }
        return entries;
    }

    
    public String read(String path) throws IOException {
        Path file = resolvePath(path);
        if (!Files.isRegularFile(file)) {
            throw new IOException("不是文件: " + file);
        }
        long size = Files.size(file);
        if (size > MAX_READ_SIZE) {
            throw new IOException("文件过大 (" + size + " bytes > 100KB), 无法在线打开, 请下载后本地修改替换");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    
    public void write(String path, String content) throws IOException {
        Path file = resolvePath(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    
    public boolean delete(String path) throws IOException {
        Path target = resolvePath(path);
        if (!Files.exists(target)) {
            return false;
        }
        if (Files.isDirectory(target) && !Files.isSymbolicLink(target)) {
            try (var stream = Files.walk(target)) {
                var paths = stream.sorted(Comparator.reverseOrder()).toList();
                for (Path p : paths) {
                    Files.deleteIfExists(p);
                }
            }
        } else {
            Files.delete(target);
        }
        return true;
    }

    
    public void mkdir(String path) throws IOException {
        Path dir = resolvePath(path);
        Files.createDirectories(dir);
    }

    
    public Map<String, Object> stat(String path) {
        Path p = resolvePath(path);
        if (!Files.exists(p)) {
            return Map.of("exists", false);
        }
        return Map.of(
                "exists", true,
                "directory", Files.isDirectory(p),
                "size", Files.isDirectory(p) ? 0 : safeSize(p));
    }

    private long safeSize(Path p) {
        try {
            return Files.size(p);
        } catch (IOException e) {
            return -1;
        }
    }

    
    
    

    
    public static Path resolveSandboxed(String baseDir, String userPath) {
        String p = (userPath == null || userPath.isBlank()) ? "." : userPath;
        return PathUtil.sanitize(baseDir, p);
    }

    public List<FileEntry> listUnder(String baseDir, String userPath) throws IOException {
        return list(resolveSandboxed(baseDir, userPath).toString());
    }

    
    public List<FileEntry> listUnderRelative(String baseDir, String userPath) throws IOException {
        Path base = Path.of(baseDir).toAbsolutePath().normalize();
        List<FileEntry> abs = list(resolveSandboxed(baseDir, userPath).toString());
        List<FileEntry> out = new ArrayList<>(abs.size());
        for (FileEntry e : abs) {
            Path ep = Path.of(e.path());
            String rel = base.relativize(ep).toString().replace('\\', '/');
            out.add(new FileEntry(e.name(), rel, e.directory(), e.size(), e.modified()));
        }
        return out;
    }

    public String readUnder(String baseDir, String userPath) throws IOException {
        return read(resolveSandboxed(baseDir, userPath).toString());
    }

    public void writeUnder(String baseDir, String userPath, String content) throws IOException {
        Path file = resolveSandboxed(baseDir, userPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
    }

    public boolean deleteUnder(String baseDir, String userPath) throws IOException {
        return delete(resolveSandboxed(baseDir, userPath).toString());
    }

    
    public void rename(String path, String newName) throws IOException {
        validateName(newName);
        Path file = resolvePath(path);
        Path target = file.getParent().resolve(newName);
        if (Files.exists(target)) {
            throw new IOException("目标已存在: " + newName);
        }
        Files.move(file, target);
    }

    
    public void renameUnder(String baseDir, String userPath, String newName) throws IOException {
        validateName(newName);
        Path file = resolveSandboxed(baseDir, userPath);
        Path target = file.getParent().resolve(newName);
        if (Files.exists(target)) {
            throw new IOException("目标已存在: " + newName);
        }
        Files.move(file, target);
    }

    
    public void copy(String srcPath, String destPath) throws IOException {
        Path src = resolvePath(srcPath);
        Path dest = resolvePath(destPath);
        doCopy(src, dest);
    }

    
    public void copyUnder(String baseDir, String srcUserPath, String destUserPath) throws IOException {
        Path src = resolveSandboxed(baseDir, srcUserPath);
        Path dest = resolveSandboxed(baseDir, destUserPath);
        doCopy(src, dest);
    }

    private void doCopy(Path src, Path dest) throws IOException {
        if (!Files.exists(src)) {
            throw new IOException("源不存在: " + src);
        }
        if (src.equals(dest)) {
            throw new IOException("源与目标相同");
        }
        if (dest.startsWith(src)) {
            throw new IOException("不能复制到自身或其子目录内");
        }
        if (Files.isDirectory(src)) {
            copyDir(src, dest);
        } else {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyDir(Path src, Path dest) throws IOException {
        Files.createDirectories(dest);
        try (java.util.stream.Stream<Path> stream = Files.walk(src)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                Path target = dest.resolve(src.relativize(p));
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void validateName(String newName) throws IOException {
        if (newName == null || newName.isBlank()) {
            throw new IOException("名称不能为空");
        }
        if (newName.contains("/") || newName.contains("\\")) {
            throw new IOException("名称不能包含路径分隔符");
        }
    }

    
    public List<FileEntry> listDrives() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        List<FileEntry> entries = new ArrayList<>();
        for (File root : File.listRoots()) {
            String abs = root.getAbsolutePath();
            String name;
            if (windows) {
                
                name = abs.replace("\\", "").replace(":", "") + ":";
            } else {
                name = "/";
            }
            entries.add(new FileEntry(name, abs, true, 0, root.lastModified() / 1000));
        }
        return entries;
    }

    public void mkdirUnder(String baseDir, String userPath) throws IOException {
        Files.createDirectories(resolveSandboxed(baseDir, userPath));
    }

    public Map<String, Object> statUnder(String baseDir, String userPath) {
        Path p = resolveSandboxed(baseDir, userPath);
        if (!Files.exists(p)) {
            return Map.of("exists", false);
        }
        return Map.of(
                "exists", true,
                "directory", Files.isDirectory(p),
                "size", Files.isDirectory(p) ? 0 : safeSize(p));
    }
}
