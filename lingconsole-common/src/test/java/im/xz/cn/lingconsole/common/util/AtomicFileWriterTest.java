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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AtomicFileWriterTest {

    @TempDir
    Path directory;

    @Test
    void atomicallyCreatesAndReplacesCompleteContent() throws Exception {
        Path target = directory.resolve("config.toml");

        AtomicFileWriter.writeString(target, "name = \"old\"\n");
        AtomicFileWriter.write(target, "name = \"new\"\n".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("name = \"new\"\n".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void usesOwnerOnlyPermissionsOnPosixFileSystems() throws Exception {
        Path target = directory.resolve("secret.toml");
        AtomicFileWriter.writeString(target, "secret");

        if (Files.getFileAttributeView(target, PosixFileAttributeView.class) != null) {
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(target));
        }
    }
}
