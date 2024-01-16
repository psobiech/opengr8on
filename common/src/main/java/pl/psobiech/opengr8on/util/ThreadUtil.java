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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;

public class ThreadUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreadUtil.class);

    private static final int MIN_RUNNABLE;

    static {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        final int parallelism = Math.max(4, availableProcessors);
        final int maxPoolSize = Math.round(parallelism * 1.2f);
        MIN_RUNNABLE = Math.min(2, availableProcessors);

        System.setProperty("jdk.virtualThreadScheduler.parallelism", String.valueOf(parallelism));
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", String.valueOf(maxPoolSize));
        System.setProperty("jdk.virtualThreadScheduler.minRunnable", String.valueOf(MIN_RUNNABLE));

        System.setProperty("jdk.tracePinnedThreads", "full/short");

        LOGGER.debug("Virtual Threads: %d-%d/%d".formatted(MIN_RUNNABLE, parallelism, maxPoolSize));
    }

    private static final ThreadFactory SHUTDOWN_THREAD_FACTORY = threadFactory("ShutdownThreads", false);

    private static final ScheduledExecutorService INSTANCE;

    static {
        INSTANCE = virtualScheduler("DEFAULT");

        addShutdownHook(INSTANCE::shutdownNow);
    }

    private ThreadUtil() {
        // NOP
    }

    /**
     * Attempts to close the executor, by sending interrupt signal to all threads and waiting for a short period of time.
     *
     * @return if executor was closed within the default timeout or false if its still pending closure
     */
    public static boolean close(ExecutorService executor) {
        if (executor == null) {
            return true;
        }

        executor.shutdownNow();

        try {
            return executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
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
        return new ScheduledThreadPoolExecutor(MIN_RUNNABLE, virtualThreadFactory(name));
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
    public static ThreadFactory virtualThreadFactory(String groupName) {
        return Thread.ofVirtual()
                     .name(groupName + "-", 1)
                     .inheritInheritableThreadLocals(true)
                     .uncaughtExceptionHandler((t, e) -> {
                         LOGGER.error("%s: %s".formatted(t.getName(), e.getMessage()), e);
                     })
                     .factory();
    }

    /**
     * @param daemon should platform thread be marked as a daemon thread
     * @return named thread factory, that produces platform threads
     */
    public static ThreadFactory threadFactory(String groupName, boolean daemon) {
        return Thread.ofPlatform()
                     .name(groupName + "-", 1)
                     .daemon(daemon)
                     .inheritInheritableThreadLocals(true)
                     .uncaughtExceptionHandler((t, e) -> {
                         LOGGER.error("%s: %s".formatted(t.getName(), e.getMessage()), e);
                     })
                     .factory();
    }
}
