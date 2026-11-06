package com.peerchat.server.db;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

// Luồng nền xử lý lưu trữ CSDL bất đồng bộ (Asynchronous DB Queue)
// Đảm bảo việc ghi MySQL không bao giờ làm block hay làm trễ luồng truyền nhận gói tin TCP
public class AsyncDbWorker implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AsyncDbWorker.class.getName());

    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;

    public void start() {
        if (running.compareAndSet(false, true)) {
            workerThread = new Thread(this, "AsyncDbWorker-Thread");
            workerThread.setDaemon(true);
            workerThread.start();
            LOGGER.info("[ASYNC_DB] Hàng đợi lưu CSDL ngầm đã khởi động.");
        }
    }

    // Đẩy một tác vụ lưu DB vào hàng đợi thực thi ngầm
    public void submit(Runnable task) {
        if (task != null && running.get()) {
            taskQueue.offer(task);
        }
    }

    @Override
    public void run() {
        while (running.get() || !taskQueue.isEmpty()) {
            try {
                Runnable task = taskQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        LOGGER.log(Level.WARNING, "[ASYNC_DB_TASK_ERR] Lỗi thực thi tác vụ DB ngầm: " + t.getMessage(), t);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void stop() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
