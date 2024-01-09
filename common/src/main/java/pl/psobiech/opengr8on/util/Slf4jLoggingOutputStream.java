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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.event.Level;

public class Slf4jLoggingOutputStream extends OutputStream {
    protected static final char[] LINE_SEPARATOR = System.lineSeparator().toCharArray();

    /**
     * The default number of bytes in the buffer.
     */
    public static final int DEFAULT_BUFFER_LENGTH = 2048;

    protected final Logger logger;

    /**
     * Used to maintain the contract of {@link #close()}.
     */
    protected boolean hasBeenClosed = false;

    /**
     * The internal buffer where data is stored.
     */
    protected byte[] buffer;

    /**
     * The number of valid bytes in the buffer.
     */
    protected int offset;

    private final Level level;

    /**
     * Creates the LoggingOutputStream to flush to the given Category.
     *
     * @param logger the Logger to write to
     * @throws IllegalArgumentException if cat == null or priority == null
     */
    public Slf4jLoggingOutputStream(Logger logger, Level level, int bufferLength) throws IllegalArgumentException {
        this.logger = logger;

        this.level = level;

        buffer = new byte[bufferLength];
        offset = 0;
    }

    /**
     * Writes the specified byte to this output stream. The general contract for <code>write</code> is that one byte is written to the output stream. The byte
     * to be written is the eight low-order bits of the argument <code>b</code>. The 24 high-order bits of <code>b</code> are ignored.
     *
     * @param b the <code>byte</code> to write
     */
    @Override
    public void write(int b) throws IOException {
        if (hasBeenClosed) {
            throw new IOException("The stream has been closed.");
        }

        // don't log nulls
        if (b == 0) {
            return;
        }

        // flush on line ending
        if (b == LINE_SEPARATOR[LINE_SEPARATOR.length - 1]) {
            if (LINE_SEPARATOR.length == 2) {
                offset -= 1;
            }

            flush();

            return;
        }

        // instead of writing past the buffer, just flush
        // (we do risk breaking utf-8 encoding though, but it's better than a possibility of a memory leak if we increase the buffer indefinitely)
        if (offset == buffer.length) {
            flush();
        }

        buffer[offset++] = (byte) b;
    }

    /**
     * Closes this output stream and releases any system resources associated with this stream. The general contract of
     * <code>close</code> is that it closes the output stream. A closed
     * stream cannot perform output operations and cannot be reopened.
     */
    @Override
    public void close() {
        flush();

        hasBeenClosed = true;
    }

    /**
     * Flushes this output stream and forces any buffered output bytes to be written out. The general contract of <code>flush</code> is that calling it is an
     * indication that, if any bytes previously written have been buffered by the implementation of the output stream, such bytes should immediately be written
     * to their intended destination.
     */
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
