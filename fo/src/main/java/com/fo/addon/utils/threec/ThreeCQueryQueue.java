package com.fo.addon.utils.threec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 3C 查询串行队列：单条 HTTP 通道，两次请求开始间隔不小于 {@code spacingMillis}，
 * 失败后至少等 {@code retryMillis} 再重试，不设总次数上限。
 *
 * <p>移植自 Tanghulu（https://github.com/Tanghulu2019-a/Tanghulu）的
 * {@code ThreeCQueryQueue}（GPLv3），保留其“失败玩家不会永久阻塞其他玩家”的排队语义。
 */
public final class ThreeCQueryQueue implements AutoCloseable {
    @FunctionalInterface
    public interface Query {
        ThreeCPlayerService.PlayerTime query(String name) throws IOException, InterruptedException;
    }

    public interface Listener {
        void success(ThreeCPlayerService.PlayerTime data);

        void retry(String name, long attempt, String error);
    }

    private static final class Job {
        final String name;
        boolean manual;
        boolean cancelled;
        long attempts;
        ScheduledFuture<?> future;

        Job(String name, boolean manual) {
            this.name = name;
            this.manual = manual;
        }
    }

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "FO-3C-Query");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Query query;
    private final Listener listener;
    private final long spacingMillis;
    private final long retryMillis;
    private long lastRequestMillis;

    public ThreeCQueryQueue(Query query, Listener listener, long spacingMillis, long retryMillis) {
        if (spacingMillis < 0 || retryMillis < 0) throw new IllegalArgumentException("Negative interval");
        this.query = query;
        this.listener = listener;
        this.spacingMillis = spacingMillis;
        this.retryMillis = retryMillis;
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public synchronized boolean enqueue(String input, boolean manual) {
        String name = ThreeCPlayerService.validateName(input);
        Job previous = jobs.get(key(name));
        if (previous != null) {
            previous.manual |= manual;
            return false;
        }
        Job job = new Job(name, manual);
        jobs.put(key(name), job);
        schedule(job, 0);
        return true;
    }

    public synchronized boolean isQueued(String input) {
        try {
            return jobs.containsKey(key(ThreeCPlayerService.validateName(input)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public synchronized void cancel(String input) {
        try {
            Job job = jobs.remove(key(ThreeCPlayerService.validateName(input)));
            if (job != null) {
                job.cancelled = true;
                if (job.future != null) job.future.cancel(false);
            }
        } catch (IllegalArgumentException ignored) {
            // 非法名字没有排队项
        }
    }

    private void schedule(Job job, long delayMillis) {
        job.future = executor.schedule(() -> run(job), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void run(Job job) {
        synchronized (this) {
            if (job.cancelled) return;
            long now = System.currentTimeMillis();
            long wait = lastRequestMillis + spacingMillis - now;
            if (wait > 0) {
                schedule(job, wait);
                return;
            }
            lastRequestMillis = now;
        }
        try {
            ThreeCPlayerService.PlayerTime data = query.query(job.name);
            synchronized (this) {
                jobs.remove(key(job.name));
            }
            listener.success(data);
        } catch (IOException | InterruptedException | RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            synchronized (this) {
                if (job.cancelled) return;
                job.attempts++;
                listener.retry(job.name, job.attempts, message);
                schedule(job, retryMillis);
            }
        }
    }

    @Override
    public synchronized void close() {
        for (Job job : jobs.values()) {
            job.cancelled = true;
            if (job.future != null) job.future.cancel(false);
        }
        jobs.clear();
        executor.shutdownNow();
    }
}
