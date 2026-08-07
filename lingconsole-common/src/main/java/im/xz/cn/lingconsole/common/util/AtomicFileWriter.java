/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.common.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;

public final class AtomicFileWriter {

    private static final FileAttribute<?> OWNER_ONLY =
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));

    private AtomicFileWriter() {
    }

    public static void writeString(Path target, String content) throws IOException {
        Objects.requireNonNull(content, "content");
        write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path target, byte[] content) throws IOException {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(content, "content");
        Path absoluteTarget = target.toAbsolutePath();
        Path parent = absoluteTarget.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Target parent directory does not exist: " + parent);
        }

        boolean posix = Files.getFileAttributeView(parent, PosixFileAttributeView.class) != null;
        Path temporary = posix
                ? Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp", OWNER_ONLY)
                : Files.createTempFile(parent, "." + absoluteTarget.getFileName() + ".", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (posix) {
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            }
            try {
                Files.move(temporary, absoluteTarget, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic replacement is not supported for " + absoluteTarget, e);
            }
            forceDirectory(parent);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            assert true;
        }
    }
}
