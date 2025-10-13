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

package pl.psobiech.opengr8on.vclu;

import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ResourceUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ServerVersion {
    private static final Properties GIT_PROPERTIES;

    static {
        final Path gitPropertiesPath = ResourceUtil.classPath("git.properties");

        GIT_PROPERTIES = new Properties();
        try (InputStream inputStream = Files.newInputStream(gitPropertiesPath)) {
            GIT_PROPERTIES.load(inputStream);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private ServerVersion() {
        // NOP
    }

    public static String get() {
        final String version = GIT_PROPERTIES.getProperty("git.commit.id.describe");
        if (version.startsWith("${")) {
            return "DEVELOPMENT";
        }

        return version;
    }
}
