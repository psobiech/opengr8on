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

package pl.psobiech.opengr8on.client;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.util.RandomUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CipherKeyTest {
    @Test
    void generateDefaultKey() {
        final byte[] expected = Base64.decodeBase64("+O7WsrhYluaV4tufuyTgVw==");
        final byte[] actual = CipherKey.generateDefaultKey("AA55AA55".getBytes());

        assertArrayEquals(expected, actual);
    }

    @Test
    void sanity() {
        final CipherKey cipherKey = Mocks.cipherKey();
        final byte[] expected = RandomUtil.bytes(Command.RANDOM_BYTES);

        //

        final byte[] actual = cipherKey.decrypt(
                        cipherKey.encrypt(expected)
                )
                .get();

        assertArrayEquals(expected, actual);
    }
}