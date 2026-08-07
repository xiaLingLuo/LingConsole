package im.xz.cn.lingconsole.daemon;

import java.util.concurrent.Semaphore;

final class FileTaskLimiter {

    @FunctionalInterface
    interface Task {
        void run() throws Exception;
    }

    private final Semaphore permits;

    FileTaskLimiter(int maxTasks) {
        permits = new Semaphore(maxTasks);
    }

    boolean tryRun(Task task) throws Exception {
        if (!permits.tryAcquire()) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            permits.release();
        }
    }
}
