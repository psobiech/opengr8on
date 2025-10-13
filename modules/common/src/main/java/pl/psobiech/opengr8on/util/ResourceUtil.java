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

import pl.psobiech.opengr8on.exceptions.UnexpectedException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.*;

/**
 * Common resource loading operations
 */
public class ResourceUtil {
    private static final String JAR_PATH_SEPARATOR = Pattern.quote("!");

    private static final ReentrantLock JAR_FILE_SYSTEMS_LOCK = new ReentrantLock();

    private static final Map<String, FileSystem> JAR_FILE_SYSTEMS = new HashMap<>();

    private ResourceUtil() {
        // NOP
    }

    /**
     * @return path, that is located in local classpath/jar or local file system
     */
    public static Path classPath(String path) {
        return classPath(URI.create("classpath:/" + path));
    }

    /**
     * @return path, that is located in local classpath/jar or local file system
     */
    public static Path classPath(URI uri) {
        final String resourceUriPath = getResourceUriPath(uri);

        final URL url = ResourceUtil.class.getResource(resourceUriPath);
        if (url == null) {
            throw new UnexpectedException(uri + " not found!");
        }

        try {
            final URI classPathUri = url.toURI();
            final String scheme = classPathUri.getScheme();
            final String classPathUriAsString = classPathUri.toString();

            final Path path;
            if (scheme.equals(SchemeEnum.JAR.toUrlScheme())) {
                final String jarPath = classPathUriAsString.split(JAR_PATH_SEPARATOR, 2)[0];

                path = getOrCreateJarFileSystemFor(jarPath).provider()
                                                           .getPath(classPathUri);
            } else {
                path = Paths.get(classPathUri);
            }

            return path;
        } catch (URISyntaxException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * @return jar file system from jar path
     */
    private static FileSystem getOrCreateJarFileSystemFor(String jarPath) {
        JAR_FILE_SYSTEMS_LOCK.lock();
        try {
            return JAR_FILE_SYSTEMS.computeIfAbsent(
                    jarPath,
                    ignored -> {
                        try {
                            final URI jarUri = URI.create(jarPath);

                            return FileSystems.newFileSystem(jarUri, Collections.emptyMap());
                        } catch (IOException e) {
                            throw new UnexpectedException(e);
                        }
                    }
            );
        } finally {
            JAR_FILE_SYSTEMS_LOCK.unlock();
        }
    }

    /**
     * @return resource path for the given URI
     */
    private static String getResourceUriPath(URI uri) {
        final String path = stripToEmpty(uri.getPath());
        final String host = stripToNull(uri.getHost());
        if (host == null) {
            return path;
        }

        return "/" + host + path;
    }

    private enum SchemeEnum {
        CLASSPATH,
        JAR,
        FILE,
        //
        ;

        public String toUrlScheme() {
            return lowerCase(name());
        }
    }
}
