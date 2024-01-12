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

package pl.psobiech.opengr8on.tftp;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import pl.psobiech.opengr8on.tftp.exceptions.TFTPPacketException;
import pl.psobiech.opengr8on.tftp.packets.TFTPErrorType;
import pl.psobiech.opengr8on.util.FileUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@Execution(ExecutionMode.SAME_THREAD)
public class TFTPServerTest {
    private static Path rootDirectory;

    @BeforeEach
    void setUp() {
        rootDirectory = FileUtil.temporaryDirectory();
        FileUtil.mkdir(rootDirectory);
    }

    @AfterEach
    void tearDown() {
        FileUtil.deleteRecursively(rootDirectory);
    }

    @Test
    void testLocationUnavailable() throws Exception {
        FileUtil.touch(rootDirectory.resolve("restricted.file"));

        final Path aDriveDirectory = rootDirectory.resolve("a");
        FileUtil.mkdir(aDriveDirectory);

        try {
            TFTPServer.parseLocation(aDriveDirectory, "../restricted.file");
        } catch (TFTPPacketException e) {
            assertEquals(TFTPErrorType.ACCESS_VIOLATION, e.getError());

            return;
        }

        fail();
    }

    @Test
    void testGoodLocationSimple() throws Exception {
        final Path expectedPath = rootDirectory.resolve("good.file");

        final Path actualPath = TFTPServer.parseLocation(rootDirectory, "good.file");

        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testGoodLocation() throws Exception {
        final Path expectedPath = rootDirectory.resolve("good.file");

        final Path actualPath = TFTPServer.parseLocation(rootDirectory, "./good.file");

        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testGoodLocationSubdirectory() throws Exception {
        final Path expectedPath = rootDirectory.resolve("c").resolve("good.file");

        final Path actualPath = TFTPServer.parseLocation(rootDirectory, "./c/good.file");

        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testGoodLocationRoot() throws Exception {
        final Path expectedPath = rootDirectory.resolve("good.file");

        final Path actualPath = TFTPServer.parseLocation(rootDirectory, "/good.file");

        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testGoodLocationRootSubdirectory() throws Exception {
        final Path expectedPath = rootDirectory.resolve("d").resolve("good.file");

        final Path actualPath = TFTPServer.parseLocation(rootDirectory, "/d/good.file");

        assertEquals(expectedPath, actualPath);
    }

    @Test
    void testGoodWindowsLocation() throws Exception {
        final Path aDriveDirectory = rootDirectory.resolve("a");
        FileUtil.mkdir(aDriveDirectory);

        final Path expectedPath = aDriveDirectory.resolve("good.file");

        final Path actualPath = TFTPServer.parseLocation(rootDirectory, "a:\\good.file");

        assertEquals(expectedPath, actualPath);
    }

}
