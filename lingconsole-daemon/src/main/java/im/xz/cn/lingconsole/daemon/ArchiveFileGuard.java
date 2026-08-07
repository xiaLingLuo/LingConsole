package im.xz.cn.lingconsole.daemon;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class ArchiveFileGuard {

    private static final long MIN_FREE_SPACE = 16L * 1024 * 1024;

    private ArchiveFileGuard() {
    }

    record Limits(long maxEntries, long maxTotalBytes, long maxSingleFileBytes, int maxPathDepth) {
    }

    record Stats(long entries, long regularFileBytes) {
    }

    static long totalRegularFileBytes(List<Path> inputs, long maxBytes) throws IOException {
        return scan(inputs, new Limits(Long.MAX_VALUE, maxBytes, maxBytes, Integer.MAX_VALUE)).regularFileBytes();
    }

    static Stats scan(List<Path> inputs, Limits limits) throws IOException {
        Set<Path> counted = new HashSet<>();
        long entries = 0;
        long total = 0;
        for (Path input : inputs) {
            if (!Files.exists(input, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("归档输入不存在: " + input);
            }
            try (var paths = Files.walk(input)) {
                Iterator<Path> iterator = paths.iterator();
                while (iterator.hasNext()) {
                    Path path = iterator.next();
                    BasicFileAttributes attrs = Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isSymbolicLink() || attrs.isOther()) {
                        throw new IOException("归档不允许符号链接或特殊文件: " + path);
                    }
                    if (++entries > limits.maxEntries()) {
                        throw new IOException("归档条目数超过限制: " + limits.maxEntries());
                    }
                    if (input.relativize(path).getNameCount() > limits.maxPathDepth()) {
                        throw new IOException("归档路径深度超过限制: " + limits.maxPathDepth());
                    }
                    if (attrs.isRegularFile() && counted.add(path.toAbsolutePath().normalize())) {
                        if (attrs.size() > limits.maxSingleFileBytes()) {
                            throw new IOException("归档单文件超过限制: " + limits.maxSingleFileBytes() + " bytes");
                        }
                        if (attrs.size() > limits.maxTotalBytes() - total) {
                            throw new IOException("归档内容超过限制: " + limits.maxTotalBytes() + " bytes");
                        }
                        total += attrs.size();
                    }
                }
            }
        }
        return new Stats(entries, total);
    }

    static Stats scanSevenZipMetadata(String listing, Limits limits) throws IOException {
        long entries = 0;
        long total = 0;
        String path = null;
        Long size = null;
        boolean directory = false;
        for (String line : listing.lines().toList()) {
            if (line.isBlank()) {
                long[] values = acceptMetadataEntry(path, size, directory, entries, total, limits);
                entries = values[0];
                total = values[1];
                path = null;
                size = null;
                directory = false;
            } else if (line.startsWith("Path = ")) {
                path = line.substring(7);
            } else if (line.startsWith("Size = ")) {
                try {
                    size = Long.parseLong(line.substring(7).trim());
                } catch (NumberFormatException e) {
                    throw new IOException("7z 元数据包含无效大小", e);
                }
            } else if (line.equals("Folder = +") || line.startsWith("Attributes = D")) {
                directory = true;
            }
        }
        long[] values = acceptMetadataEntry(path, size, directory, entries, total, limits);
        return new Stats(values[0], values[1]);
    }

    private static long[] acceptMetadataEntry(String path, Long size, boolean directory, long entries, long total,
                                               Limits limits) throws IOException {
        if (path == null) {
            return new long[]{entries, total};
        }
        Path relative;
        try {
            relative = Path.of(path).normalize();
        } catch (RuntimeException e) {
            throw new IOException("归档包含无效路径: " + path, e);
        }
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("归档包含越界路径: " + path);
        }
        if (relative.getNameCount() > limits.maxPathDepth()) {
            throw new IOException("归档路径深度超过限制: " + limits.maxPathDepth());
        }
        entries++;
        if (entries > limits.maxEntries()) {
            throw new IOException("归档条目数超过限制: " + limits.maxEntries());
        }
        if (!directory) {
            if (size == null || size < 0) {
                throw new IOException("归档文件缺少有效展开大小: " + path);
            }
            if (size > limits.maxSingleFileBytes()) {
                throw new IOException("归档单文件超过限制: " + limits.maxSingleFileBytes() + " bytes");
            }
            if (size > limits.maxTotalBytes() - total) {
                throw new IOException("归档声明展开大小超过限制: " + limits.maxTotalBytes() + " bytes");
            }
            total += size;
        }
        return new long[]{entries, total};
    }

    static Stats monitorExtracted(Path temporary, Limits limits) throws IOException {
        Stats stats = scan(List.of(temporary), limits);
        FileStore store = Files.getFileStore(temporary);
        if (store.getUsableSpace() < MIN_FREE_SPACE) {
            throw new IOException("解压所在磁盘可用空间低于安全余量");
        }
        return stats;
    }

    static void checkArchiveFile(Path archive, long maxBytes) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(
                archive, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || attrs.size() > maxBytes) {
            throw new IOException("归档文件无效或超过限制: " + maxBytes + " bytes");
        }
    }

    static void mergeExtracted(Path source, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (var paths = Files.walk(source)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                BasicFileAttributes attrs = Files.readAttributes(
                        path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isSymbolicLink() || attrs.isOther()) {
                    throw new IOException("解压结果包含符号链接或特殊文件: " + path);
                }
                Path target = destination.resolve(source.relativize(path));
                if (attrs.isDirectory()) {
                    Files.createDirectories(target);
                } else if (attrs.isRegularFile()) {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            Iterator<Path> iterator = paths.sorted(Comparator.reverseOrder()).iterator();
            while (iterator.hasNext()) {
                Files.deleteIfExists(iterator.next());
            }
        }
    }
}
