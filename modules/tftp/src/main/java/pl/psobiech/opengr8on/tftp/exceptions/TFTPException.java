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

package pl.psobiech.opengr8on.tftp.exceptions;

import java.io.IOException;
import java.net.InetAddress;

import pl.psobiech.opengr8on.tftp.packets.TFTPErrorPacket;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;

/**
 * TFTP runtime exception, should abort any pending transfer without any response to the client
 */
public class TFTPException extends RuntimeException {
    private final TFTPErrorType error;

    public TFTPException(TFTPErrorPacket errorPacket) {
        this(errorPacket.getError(), errorPacket.getMessage());
    }

    public TFTPException(TFTPErrorType error, IOException exception) {
        super(exception);

        this.error = error;
    }

    public TFTPException(TFTPErrorType error, String message) {
        super(message);

        this.error = error;
    }

    public TFTPException(TFTPErrorType error, String message, Throwable throwable) {
        super(message, throwable);

        this.error = error;
    }

    public TFTPException(String message) {
        super(message);

        this.error = TFTPErrorType.UNDEFINED;
    }

    public TFTPErrorPacket asError(InetAddress address, int port) {
        final String message;
        final Throwable cause = getCause();
        if (cause == null) {
            message = getMessage();
        } else {
            message = cause.getClass().getSimpleName() + ": " + getMessage();
        }

        return new TFTPErrorPacket(
            address, port,
            getError(), message
        );
    }

    public TFTPErrorType getError() {
        return error;
    }
}
