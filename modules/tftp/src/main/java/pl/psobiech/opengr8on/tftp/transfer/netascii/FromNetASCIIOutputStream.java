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

package pl.psobiech.opengr8on.tftp.transfer.netascii;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class wraps an output stream, replacing all occurrences of &lt;CR&gt;&lt;LF&gt; (carriage return followed by a linefeed), which is the NETASCII standard
 * for representing a newline, with the local line separator representation. You would use this class to implement ASCII file transfers requiring conversion
 * from NETASCII.
 * <p>
 * Because of the translation process, a call to <code>flush()</code> will not flush the last byte written if that byte was a carriage return. A call to
 * {@link #close close() }, however, will flush the carriage return.
 */
public final class FromNetASCIIOutputStream extends FilterOutputStream {
    private final ReentrantLock lock = new ReentrantLock();

    private boolean lastWasCR;

    /**
     * Creates a FromNetASCIIOutputStream instance that wraps an existing OutputStream.
     *
     * @param output The OutputStream to wrap.
     */
    public FromNetASCIIOutputStream(final OutputStream output) {
        super(output);

        lastWasCR = false;
    }

    /**
     * Closes the stream, writing all pending data.
     *
     * @throws IOException If an error occurs while closing the stream.
     */
    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            if (NetAsciiUtil.NO_CONVERSION_REQUIRED) {
                super.close();

                return;
            }

            if (lastWasCR) {
                out.write('\r');
            }

            super.close();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes a byte array to the stream.
     *
     * @param buffer The byte array to write.
     * @throws IOException If an error occurs while writing to the underlying stream.
     */
    @Override
    public void write(final byte[] buffer) throws IOException {
        lock.lock();
        try {
            write(buffer, 0, buffer.length);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes a number of bytes from a byte array to the stream starting from a given offset.
     *
     * @param buffer The byte array to write.
     * @param offset The offset into the array at which to start copying data.
     * @param length The number of bytes to write.
     * @throws IOException If an error occurs while writing to the underlying stream.
     */
    @Override
    public void write(final byte[] buffer, int offset, int length) throws IOException {
        lock.lock();
        try {
            if (NetAsciiUtil.NO_CONVERSION_REQUIRED) {
                // FilterOutputStream method is very slow.
                // super.write(buffer, offset, length);
                out.write(buffer, offset, length);
                return;
            }

            while (length-- > 0) {
                writeInt(buffer[offset++]);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes a byte to the stream. Note that a call to this method might not actually write a byte to the underlying stream until a subsequent character is
     * written, from which it can be determined if a NETASCII line separator was encountered. This is transparent to the programmer and is only mentioned for
     * completeness.
     *
     * @param ch The byte to write.
     * @throws IOException If an error occurs while writing to the underlying stream.
     */
    @Override
    public void write(final int ch) throws IOException {
        lock.lock();
        try {
            if (NetAsciiUtil.NO_CONVERSION_REQUIRED) {
                out.write(ch);
                return;
            }

            writeInt(ch);
        } finally {
            lock.unlock();
        }
    }

    private void writeInt(final int ch) throws IOException {
        switch (ch) {
            case '\r':
                lastWasCR = true;
                // Don't write anything. We need to see if next one is linefeed
                break;
            case '\n':
                if (lastWasCR) {
                    out.write(NetAsciiUtil.LINE_SEPARATOR_BYTES);
                    lastWasCR = false;
                    break;
                }

                out.write('\n');
                break;
            default:
                if (lastWasCR) {
                    out.write('\r');
                    lastWasCR = false;
                }

                out.write(ch);
                break;
        }
    }
}
