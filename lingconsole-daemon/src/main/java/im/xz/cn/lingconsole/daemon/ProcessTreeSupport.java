package im.xz.cn.lingconsole.daemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

final class ProcessTreeSupport {

    private static final Logger log = LoggerFactory.getLogger(ProcessTreeSupport.class);

    private ProcessTreeSupport() {
    }

    static void terminate(Process process) {
        ProcessHandle root;
        try {
            root = process.toHandle();
        } catch (UnsupportedOperationException e) {
            process.destroyForcibly();
            return;
        }
        List<ProcessHandle> tree = snapshot(root);
        destroy(tree, false);
        waitBriefly(tree);
        tree = merge(tree, snapshot(root));
        destroy(tree, true);
        waitBriefly(tree);
        if (isWindows() && root.isAlive()) {
            try {
                Process taskkill = new ProcessBuilder("taskkill", "/PID", Long.toString(root.pid()), "/T", "/F")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                taskkill.waitFor(3, TimeUnit.SECONDS);
            } catch (IOException e) {
                log.debug("taskkill 执行失败: PID={}", root.pid(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static List<ProcessHandle> snapshot(ProcessHandle root) {
        List<ProcessHandle> result = new ArrayList<>();
        try (Stream<ProcessHandle> descendants = root.descendants()) {
            descendants.forEach(result::add);
        } catch (RuntimeException e) {
            log.debug("无法枚举进程树: PID={}", root.pid(), e);
        }
        result.add(root);
        return result;
    }

    private static List<ProcessHandle> merge(List<ProcessHandle> first, List<ProcessHandle> second) {
        Map<Long, ProcessHandle> merged = new LinkedHashMap<>();
        Stream.concat(first.stream(), second.stream()).forEach(handle -> merged.put(handle.pid(), handle));
        return new ArrayList<>(merged.values());
    }

    private static void destroy(List<ProcessHandle> handles, boolean force) {
        for (ProcessHandle handle : handles) {
            if (handle.isAlive()) {
                try {
                    if (force) {
                        handle.destroyForcibly();
                    } else {
                        handle.destroy();
                    }
                } catch (RuntimeException e) {
                    log.debug("终止进程失败: PID={}", handle.pid(), e);
                }
            }
        }
    }

    private static void waitBriefly(List<ProcessHandle> handles) {
        CompletableFuture<?> all = CompletableFuture.allOf(
                handles.stream().map(ProcessHandle::onExit).toArray(CompletableFuture[]::new));
        try {
            all.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ignored) {
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
