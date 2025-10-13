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

import pl.psobiech.opengr8on.util.FileUtil;

import java.nio.charset.StandardCharsets;

public final class NetAsciiUtil {
    public static final int END_OF_STREAM = -1;

    static final boolean NO_CONVERSION_REQUIRED;

    static final String LINE_SEPARATOR;

    static final byte[] LINE_SEPARATOR_BYTES;

    static {
        LINE_SEPARATOR = System.lineSeparator();
        NO_CONVERSION_REQUIRED = LINE_SEPARATOR.equals(FileUtil.CRLF);
        LINE_SEPARATOR_BYTES = LINE_SEPARATOR.getBytes(StandardCharsets.US_ASCII);
    }

    private NetAsciiUtil() {
        // NOP
    }

    /**
     * Returns true if the NetASCII line separator differs from the system line separator, false if they are the same. This method is useful to determine
     * whether you need to instantiate a FromNetASCIIInputStream object.
     *
     * @return True if the NETASCII line separator differs from the local system line separator, false if they are the same.
     */
    public static boolean isConversionRequired() {
        return !NO_CONVERSION_REQUIRED;
    }
}
