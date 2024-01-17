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

package pl.psobiech.opengr8on.exceptions;

import java.net.SocketException;

/**
 * Exception thrown when a thread was interrupted, should abort all processing of the given thread
 */
public class UncheckedInterruptedException extends RuntimeException {
    private static final String SOCKET_INTERRUPTED_MESSAGE = "Closed by interrupt";

    public UncheckedInterruptedException(InterruptedException e) {
        super(e.getMessage(), e);

        Thread.currentThread().interrupt();
    }

    public UncheckedInterruptedException(SocketException e) {
        super(e.getMessage(), e);

        Thread.currentThread().interrupt();
    }

    /**
     * @return true, if the SocketException was caused by InterruptedException
     */
    public static boolean wasSocketInterrupted(SocketException e) {
        return e.getMessage().equalsIgnoreCase(SOCKET_INTERRUPTED_MESSAGE);
    }
}
