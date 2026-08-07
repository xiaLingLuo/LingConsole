/*
 * LingConsole - A Server WebUI control panel
 * Copyright (C) 2026  XIAZHIRUI HUANG
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package im.xz.cn.lingconsole.daemon.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemServiceSandboxTest {

    @TempDir
    Path tempDir;

    @Test
    void protectedAppFilesRejectSymlinkEscapeButOptOutAllowsIt() throws Exception {
        Path base = Files.createDirectory(tempDir.resolve("app"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "outside");
        Path link = base.resolve("linked");
        createLinkOrJunction(link, outside);

        FileSystemService files = new FileSystemService();
        assertThrows(IOException.class,
                () -> files.readUnder(base.toString(), "linked/secret.txt", true));
        assertThrows(IOException.class,
                () -> files.writeUnder(base.toString(), "linked/new.txt", "blocked", true));

        assertEquals("outside", files.readUnder(base.toString(), "linked/secret.txt", false));
        files.writeUnder(base.toString(), "linked/new.txt", "allowed", false);
        assertEquals("allowed", Files.readString(outside.resolve("new.txt")));
    }

    @Test
    void protectedWriteCreatesOnlyNormalDirectories() throws Exception {
        Path base = Files.createDirectory(tempDir.resolve("app"));
        FileSystemService files = new FileSystemService();

        files.writeUnder(base.toString(), "one/two/file.txt", "content", true);

        assertEquals("content", Files.readString(base.resolve("one/two/file.txt")));
    }

    @Test
    void directoryListingRejectsMoreThanConfiguredLimit() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("large-directory"));
        Files.createFile(directory.resolve("one"));
        Files.createFile(directory.resolve("two"));
        Files.createFile(directory.resolve("three"));

        FileSystemService files = new FileSystemService(2);

        IOException error = assertThrows(IOException.class, () -> files.list(directory.toString()));
        org.junit.jupiter.api.Assertions.assertTrue(error.getMessage().contains("目录条目数超过限制: 2"));
    }

    private void createLinkOrJunction(Path link, Path target) throws Exception {
        try {
            Files.createSymbolicLink(link, target);
            return;
        } catch (IOException | UnsupportedOperationException | SecurityException symlinkError) {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
                Assumptions.abort("当前环境不能创建符号链接: " + symlinkError.getMessage());
            }
            Process process = new ProcessBuilder("cmd.exe", "/c",
                    "mklink /J \"" + link + "\" \"" + target + "\"")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            if (process.waitFor() != 0 || !Files.exists(link)) {
                Assumptions.abort("当前环境不能创建 Junction: " + output);
            }
        }
    }
}
