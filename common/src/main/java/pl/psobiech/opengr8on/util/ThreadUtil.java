/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.util;

import java.util.concurrent.ThreadFactory;
import java.util.function.BiFunction;

public class ThreadUtil {
    private static final ThreadFactory DEFAULT_DAEMON_FACTORY = daemonThreadFactory("default");

    private static final ThreadFactory SHUTDOWN_HOOK_FACTORY = threadFactory(false, "shutdownHooks", Thread::new);

    private ThreadUtil() {
        // NOP
    }

    public static void shutdownHook(Runnable runnable) {
        Runtime.getRuntime().addShutdownHook(
            SHUTDOWN_HOOK_FACTORY.newThread(runnable)
        );
    }

    public static Thread newDaemonThread(Runnable runnable) {
        return DEFAULT_DAEMON_FACTORY.newThread(runnable);
    }

    public static ThreadFactory daemonThreadFactory(String groupName) {
        return daemonThreadFactory(groupName, Thread::new);
    }

    public static ThreadFactory daemonThreadFactory(String groupName, BiFunction<ThreadGroup, Runnable, Thread> supplier) {
        return threadFactory(true, groupName, supplier);
    }

    public static ThreadFactory threadFactory(boolean daemon, String groupName, BiFunction<ThreadGroup, Runnable, Thread> supplier) {
        final ThreadGroup threadGroup = new ThreadGroup(groupName);

        return runnable -> {
            final Thread thread = supplier.apply(threadGroup, runnable);
            thread.setDaemon(daemon);

            return thread;
        };
    }
}
