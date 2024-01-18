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

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common IO operations
 */
public final class IOUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(IOUtil.class);

    private IOUtil() {
        // NOP
    }

    /**
     * Attempts to silently close all closables
     */
    public static void closeQuietly(Collection<? extends AutoCloseable> closeables) {
        for (AutoCloseable closeable : closeables) {
            closeQuietly(closeable);
        }
    }

    /**
     * Attempts to silently close all closables
     */
    public static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            closeQuietly(closeable);
        }
    }

    /**
     * Attempts to silently close the closable
     */
    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
    }
}
