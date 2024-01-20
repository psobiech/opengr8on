/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtil.class);

    private static final int MIN_RUNNABLE;

    static {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        final int parallelism = Math.max(4, availableProcessors);
        final int maxPoolSize = Math.round(parallelism * 1.2f);
        MIN_RUNNABLE = Math.min(4, availableProcessors);

        System.setProperty("jdk.virtualThreadScheduler.parallelism", String.valueOf(parallelism));
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", String.valueOf(maxPoolSize));
        System.setProperty("jdk.virtualThreadScheduler.minRunnable", String.valueOf(MIN_RUNNABLE));

        System.setProperty("jdk.tracePinnedThreads", "full");

        LOGGER.debug("Virtual Threads: %d-%d/%d".formatted(MIN_RUNNABLE, parallelism, maxPoolSize));

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            LOGGER.error("UncaughtException: [{}] {}", t.getName(), e.getMessage(), e);
        });
    }

    private static final ThreadFactory SHUTDOWN_THREAD_FACTORY = platformThreadFactory("ShutdownThreads", false);

    private static final ScheduledExecutorService INSTANCE;

    static {
        INSTANCE = virtualScheduler(ThreadUtil.class);

        addShutdownHook(() -> closeQuietly(INSTANCE));
    }

    private ThreadUtil() {
        // NOP
    }

    public static <T> T await(Future<T> future) {
        if (future != null && !future.isDone()) {
            try {
                return future.get();
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();

                LOGGER.error(cause.getMessage(), cause);
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }

        return null;
    }

    /**
     * Attempts to close the executor, by sending interrupt signal to all threads and waiting for a short period of time.
     *
     * @return if executor was closed within the default timeout or false if its still pending closure
     */
    public static boolean closeQuietly(ExecutorService executor) {
        if (executor == null) {
            return true;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                executor.shutdownNow();

                return executor.awaitTermination(1, TimeUnit.SECONDS);
            }

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            LOGGER.warn(e.getMessage(), e);

            executor.shutdownNow();

            return false;
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);

            executor.shutdownNow();

            return false;
        }
    }

    /**
     * Attempts to cancel the future (by sending interrupt signal)
     */
    public static void cancel(Future<?> future) {
        if (future == null) {
            return;
        }

        future.cancel(true);
    }

    /**
     * @param runnable new runnable to be executed on application shutdown
     */
    public static void addShutdownHook(Runnable runnable) {
        Runtime.getRuntime()
               .addShutdownHook(
                   SHUTDOWN_THREAD_FACTORY.newThread(runnable)
               );
    }

    /**
     * @return shared scheduled executor service, working on Virtual Threads
     */
    public static ScheduledExecutorService getInstance() {
        return INSTANCE;
    }

    /**
     * @return named scheduled executor, working on Virtual Threads
     */
    public static ScheduledExecutorService virtualScheduler(Class<?> clazz) {
        return virtualScheduler(clazz.getSimpleName());
    }

    /**
     * @return named scheduled executor, working on Virtual Threads
     */
    public static ScheduledExecutorService virtualScheduler(String name) {
        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(Integer.MAX_VALUE, virtualThreadFactory(name));
        scheduler.setKeepAliveTime(1, TimeUnit.MINUTES);
        scheduler.allowCoreThreadTimeOut(true);

        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);

        return scheduler;
    }

    /**
     * @return named scheduled executor, working on Daemon Platform Threads
     */
    public static ScheduledExecutorService daemonScheduler(Class<?> clazz) {
        return daemonScheduler(clazz.getSimpleName());
    }

    /**
     * @return named scheduled executor, working on Daemon Platform Threads
     */
    public static ScheduledExecutorService daemonScheduler(String name) {
        return daemonScheduler(MIN_RUNNABLE, name);
    }

    /**
     * @return named scheduled executor, working on Daemon Platform Threads
     */
    public static ScheduledExecutorService daemonScheduler(int poolSize, String name) {
        final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(poolSize, ThreadUtil.platformThreadFactory(name, true));
        scheduler.setKeepAliveTime(1, TimeUnit.MINUTES);
        scheduler.allowCoreThreadTimeOut(true);

        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setRemoveOnCancelPolicy(true);

        return scheduler;
    }

    /**
     * @return named scheduled executor, working on Virtual Threads
     */
    public static ExecutorService virtualExecutor(String name) {
        return Executors.newThreadPerTaskExecutor(virtualThreadFactory(name));
    }

    /**
     * @return named thread factory, that produces virtual threads
     */
    private static ThreadFactory virtualThreadFactory(String groupName) {
        return Thread.ofVirtual()
                     .name(groupName)
                     .uncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler())
                     .factory();
    }

    /**
     * @return named scheduled executor, working on Daemon Platform Threads
     */
    public static ExecutorService daemonExecutor(Class<?> clazz) {
        return daemonExecutor(clazz.getSimpleName());
    }

    /**
     * @return named scheduled executor, working on Daemon Platform Threads
     */
    public static ExecutorService daemonExecutor(String name) {
        return Executors.newCachedThreadPool(platformThreadFactory(name, true));
    }

    /**
     * @param daemon should platform thread be marked as a daemon thread
     * @return named thread factory, that produces platform threads
     */
    private static ThreadFactory platformThreadFactory(String groupName, boolean daemon) {
        return Thread.ofPlatform()
                     .name(groupName)
                     .daemon(daemon)
                     .uncaughtExceptionHandler(Thread.getDefaultUncaughtExceptionHandler())
                     .factory();
    }
}
