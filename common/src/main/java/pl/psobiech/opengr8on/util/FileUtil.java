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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

public final class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    private static final String TMPDIR_PROPERTY = "java.io.tmpdir";

    private static final String TEMPORARY_FILE_PREFIX = "tmp_";

    private static final Pattern DISALLOWED_FILENAME_CHARACTERS = Pattern.compile("[/\\\\:*?\"<>|\0]+");

    private static final Pattern WHITESPACE_CHARACTERS = Pattern.compile("\\s+");

    private static final Path TEMPORARY_DIRECTORY;

    private static final TemporaryFileTracker FILE_TRACKER = new TemporaryFileTracker();

    static {
        try {
            TEMPORARY_DIRECTORY = Files.createTempDirectory(
                Paths.get(System.getProperty(TMPDIR_PROPERTY))
                     .toAbsolutePath(),
                TEMPORARY_FILE_PREFIX
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        ThreadUtil.shutdownHook(() -> {
            try {
                Files.walkFileTree(TEMPORARY_DIRECTORY, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        deleteQuietly(file);

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                        deleteQuietly(dir);

                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                LOGGER.warn(e.getMessage(), e);
            }

            deleteQuietly(TEMPORARY_DIRECTORY);
        });

        ThreadUtil.getInstance()
                  .scheduleAtFixedRate(FILE_TRACKER::log, 1, 1, TimeUnit.MINUTES);

        mkdir(TEMPORARY_DIRECTORY);
    }

    private FileUtil() {
        // NOP
    }

    public static Path temporaryDirectory() {
        return temporaryDirectory(null);
    }

    public static Path temporaryDirectory(Path parentPath) {
        try {
            return Files.createTempDirectory(
                            parentPath == null ? TEMPORARY_DIRECTORY : parentPath,
                            TEMPORARY_FILE_PREFIX
                        )
                        .toAbsolutePath();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static Path temporaryFile() {
        return temporaryFile(null, null);
    }

    public static Path temporaryFile(String fileName) {
        return temporaryFile(null, fileName);
    }

    public static Path temporaryFile(Path parentPath) {
        return temporaryFile(parentPath, null);
    }

    public static Path temporaryFile(Path parentPath, String fileName) {
        try {
            return FILE_TRACKER.tracked(
                Files.createTempFile(
                         parentPath == null ? TEMPORARY_DIRECTORY : parentPath,
                         TEMPORARY_FILE_PREFIX, sanitize(fileName)
                     )
                     .toAbsolutePath()
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void mkdir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void touch(Path path) {
        try {
            Files.newOutputStream(path, StandardOpenOption.CREATE).close();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void linkOrCopy(Path from, Path to) {
        deleteQuietly(to);

        if (!SystemUtils.IS_OS_WINDOWS && !Files.exists(to)) {
            try {
                Files.createLink(to, from);

                return;
            } catch (Exception e) {
                // log exception and revert to copy
                LOGGER.trace(e.getMessage(), e);
            }
        }

        try {
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static void deleteQuietly(Path... paths) {
        for (Path path : paths) {
            deleteQuietly(path);
        }
    }

    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            final boolean isFileOrLinkOrDoesNotExist = !Files.isDirectory(path);
            if (isFileOrLinkOrDoesNotExist) {
                LOGGER.warn(e.getMessage(), e);
            } else if (LOGGER.isTraceEnabled()) {
                // directories might be not-empty, hence not removable
                LOGGER.trace(e.getMessage(), e);
            }
        }
    }

    public static void deleteRecursively(Path rootDirectory) {
        if (rootDirectory == null) {
            return;
        }

        try {
            Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    FileUtil.deleteQuietly(file);

                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                    FileUtil.deleteQuietly(directory);

                    return super.postVisitDirectory(directory, exc);
                }
            });
        } catch (IOException e) {
            LOGGER.warn(e.getMessage(), e);
        }

        FileUtil.deleteQuietly(rootDirectory);
    }

    public static void closeQuietly(AutoCloseable... closeables) {
        for (AutoCloseable closeable : closeables) {
            closeQuietly(closeable);
        }
    }

    public static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }

        try {
            closeable.close();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
        }
    }

    public static String sanitize(String fileName) {
        fileName = StringUtils.stripToNull(fileName);
        if (fileName == null) {
            return null;
        }

        final String fileNameNoDisallowedCharacters = DISALLOWED_FILENAME_CHARACTERS.matcher(fileName)
                                                                                    .replaceAll("_");

        return WHITESPACE_CHARACTERS.matcher(fileNameNoDisallowedCharacters)
                                    .replaceAll("_");
    }

    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    public static class TemporaryFileTracker {
        private final Map<String, UnexpectedException> stacktraces = new HashMap<>();

        private final Map<Path, Boolean> reachablePaths = new WeakHashMap<>();

        private TemporaryFileTracker() {
            // NOP
        }

        public void log() {
            final Map<Path, UnexpectedException> unreachablePaths = new HashMap<>();

            synchronized (stacktraces) {
                final Iterator<Entry<String, UnexpectedException>> iterator = stacktraces.entrySet().iterator();
                while (iterator.hasNext()) {
                    final Entry<String, UnexpectedException> entry = iterator.next();

                    final Path path = Paths.get(entry.getKey());
                    synchronized (reachablePaths) {
                        if (reachablePaths.containsKey(path)) {
                            continue;
                        }
                    }

                    iterator.remove();

                    if (Files.exists(path)) {
                        unreachablePaths.put(path, entry.getValue());
                    }
                }
            }

            for (Entry<Path, UnexpectedException> entry : unreachablePaths.entrySet()) {
                final Path path = entry.getKey();

                if (Files.exists(path)) {
                    final UnexpectedException e = entry.getValue();

                    LOGGER.warn(e.getMessage(), e);
                }
            }
        }

        public Path tracked(Path path) {
            final Path absolutePath = path.toAbsolutePath();
            final String absolutePathAsString = absolutePath.toString();

            final UnexpectedException stacktrace =
                new UnexpectedException("Temporary Path went out of scope, and the file was not removed: " + absolutePathAsString);
            synchronized (stacktraces) {
                stacktraces.put(absolutePathAsString, stacktrace);
            }

            synchronized (reachablePaths) {
                reachablePaths.put(absolutePath, Boolean.TRUE);
            }

            return absolutePath;
        }
    }
}
