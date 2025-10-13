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

import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Redirect stream output to slf4j logger. Splits newlines to separate log events.
 * Based on Log4j implementation, but without infinite scaling buffer.
 */
public class Slf4jLoggingOutputStream extends OutputStream {
    /**
     * The default number of bytes in the buffer.
     */
    public static final int DEFAULT_MAXIMUM_BUFFER_SIZE = 10240;

    private static final char[] LINE_SEPARATOR = System.lineSeparator().toCharArray();

    /**
     * Initial buffer size
     */
    private static final int INITIAL_BUFFER_SIZE = 2048;

    /**
     * Maximum size of the buffer
     */
    private final int maxBufferSize;

    /**
     * Logger where the output should be redirected
     */
    private final Logger logger;

    /**
     * Requested log level
     */
    private final Level level;

    /**
     * The internal buffer where data is stored.
     */
    private byte[] buffer;

    /**
     * The number of bytes written to the buffer
     */
    private int offset;

    /**
     * @param logger the Logger to write to
     * @param level  log level of the log
     */
    public Slf4jLoggingOutputStream(Logger logger, Level level) {
        this(logger, level, DEFAULT_MAXIMUM_BUFFER_SIZE);
    }

    /**
     * @param logger        the Logger to write to
     * @param level         log level of the log
     * @param maxBufferSize buffer that determines the maximum length of line to be flushed as a single log
     */
    public Slf4jLoggingOutputStream(Logger logger, Level level, int maxBufferSize) {
        this.logger = logger;
        this.level = level;

        this.maxBufferSize = maxBufferSize;
        this.buffer = new byte[Math.min(maxBufferSize, INITIAL_BUFFER_SIZE)];
        this.offset = 0;
    }

    @Override
    public void write(int b) throws IOException {
        if (buffer == null) {
            throw new IOException("The stream has been closed.");
        }

        // don't log nulls
        if (b == 0) {
            return;
        }

        // flush on line ending
        if (b == LINE_SEPARATOR[LINE_SEPARATOR.length - 1]) {
            if (LINE_SEPARATOR.length == 2) {
                // omit previous line separator part if line separator has 2 characters
                offset -= 1;
            }

            flush();

            return;
        }

        final int currentBufferLength = buffer.length;
        if (offset == currentBufferLength) {
            if (currentBufferLength < maxBufferSize) {
                // expand the buffer size, up to the maximum
                final int newBufferSize = Math.min(currentBufferLength * 2, maxBufferSize);

                buffer = Arrays.copyOf(buffer, newBufferSize);
            } else {
                // instead of writing past the buffer, just flush (we do risk breaking utf-8 encoding though,
                // but imho it's better than a possibility of a memory leak if we increase the buffer indefinitely)

                flush();
            }
        }

        buffer[offset++] = (byte) b;
    }

    @Override
    public void close() {
        flush();

        buffer = null;
    }

    @Override
    public void flush() {
        if (offset == 0) {
            return;
        }

        // don't print out blank lines; flushing from PrintStream puts out these
        if (offset == LINE_SEPARATOR.length) {
            if (((char) buffer[LINE_SEPARATOR.length - 1]) == LINE_SEPARATOR[LINE_SEPARATOR.length - 1]
                    && (LINE_SEPARATOR.length == 2 && ((char) buffer[0]) == LINE_SEPARATOR[0])) {
                reset();

                return;
            }
        }

        final String log = new String(Arrays.copyOf(buffer, offset), StandardCharsets.UTF_8);

        reset();
        logger.makeLoggingEventBuilder(level)
              .log(log);
    }

    private void reset() {
        offset = 0;
    }
}
