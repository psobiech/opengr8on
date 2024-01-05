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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class ThreadUtil {
    static {
        System.setProperty("jdk.virtualThreadScheduler.parallelism", "8");
        System.setProperty("jdk.virtualThreadScheduler.maxPoolSize", "8");
        System.setProperty("jdk.virtualThreadScheduler.minRunnable", "2");

        System.setProperty("jdk.tracePinnedThreads", "full/short");
    }

    private static final ThreadFactory SHUTDOWN_HOOK_FACTORY = threadFactory("shutdownHooks");

    private static final ScheduledExecutorService INSTANCE;

    static {
        INSTANCE = executor("DEFAULT");

        shutdownHook(INSTANCE::shutdownNow);
    }

    private ThreadUtil() {
        // NOP
    }

    public static void shutdownHook(Runnable runnable) {
        Runtime.getRuntime().addShutdownHook(
            SHUTDOWN_HOOK_FACTORY.newThread(runnable)
        );
    }

    public static ScheduledExecutorService getInstance() {
        return INSTANCE;
    }

    public static ScheduledExecutorService executor(String name) {
        return Executors.newScheduledThreadPool(Integer.MAX_VALUE, virtualThreadFactory(name));
    }

    public static ThreadFactory daemonThreadFactory(String groupName) {
        return virtualThreadFactory(groupName);
    }

    public static ThreadFactory virtualThreadFactory(String groupName) {
        return Thread.ofVirtual()
                     .name(groupName)
                     .inheritInheritableThreadLocals(true)
                     .factory();
    }

    public static ThreadFactory threadFactory(String groupName) {
        return Thread.ofPlatform()
                     .name(groupName)
                     .daemon(false)
                     .inheritInheritableThreadLocals(true)
                     .factory();
    }
}
