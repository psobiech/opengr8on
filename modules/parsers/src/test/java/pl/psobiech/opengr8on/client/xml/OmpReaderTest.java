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

package pl.psobiech.opengr8on.client.xml;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.ResourceUtil;
import pl.psobiech.opengr8on.xml.omp.OmpReader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class OmpReaderTest {
    @Test
    void loadCipherKey() {
        final CipherKey cipherKey = OmpReader.readProjectCipherKey(ResourceUtil.classPath("test.omp"));

        assertArrayEquals(cipherKey.getSecretKey(), Base64.decodeBase64("vd/TDJU8awHhOBn6h3YsxQ=="));
        assertArrayEquals(cipherKey.getIV(), Base64.decodeBase64("oykoaQgbuuhYOnE9CUdrnQ=="));
    }
}
