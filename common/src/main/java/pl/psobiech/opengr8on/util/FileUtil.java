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
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;

/**
 * Common nio file operations
 */
public final class FileUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    private static final String TMPDIR_PROPERTY = "java.io.tmpdir";

    private static final String TEMPORARY_FILE_PREFIX = "tmp_";

    private static final String TEMPORARY_FILE_SUFFIX = ".tmp";

    private static final Pattern DISALLOWED_FILENAME_CHARACTERS = Pattern.compile("[/\\\\:*?\"<>|\0]+");

    private static final Pattern WHITESPACE_CHARACTERS = Pattern.compile("\\s+");

    private static final Path TEMPORARY_DIRECTORY;

    private static final TemporaryFileTracker FILE_TRACKER = new TemporaryFileTracker();

    public static final String CR = Character.toString(0x0D);

    public static final String LF = Character.toString(0x0A);

    public static final String CRLF = CR + LF;

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

        ThreadUtil.getInstance()
                  .scheduleAtFixedRate(FILE_TRACKER::log, 1, 1, TimeUnit.MINUTES);

        mkdir(TEMPORARY_DIRECTORY);
        ThreadUtil.shutdownHook(() -> FileUtil.deleteRecursively(TEMPORARY_DIRECTORY));
    }

    private FileUtil() {
        // NOP
    }

    /**
     * @return a temporary directory, with unique name
     */
    public static Path temporaryDirectory() {
        return temporaryDirectory(null);
    }

    /**
     * @return a directory with unique name, as subdirectory of the supplied parentPath
     */
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

    /**
     * @return a temporary file with unique name
     */
    public static Path temporaryFile() {
        return temporaryFile(null, TEMPORARY_FILE_SUFFIX);
    }

    /**
     * @return a temporary file with unique name, suffixed with fileNameSuffix
     */
    public static Path temporaryFile(String fileNameSuffix) {
        return temporaryFile(null, fileNameSuffix);
    }

    /**
     * @return a temporary file with unique name, located in the provided parentPath
     */
    public static Path temporaryFile(Path parentPath) {
        return temporaryFile(parentPath, TEMPORARY_FILE_SUFFIX);
    }

    /**
     * @return a temporary file with unique name, located in the provided parentPath, suffixed with fileNameSuffix
     */
    public static Path temporaryFile(Path parentPath, String fileNameSuffix) {
        try {
            return FILE_TRACKER.tracked(
                Files.createTempFile(
                         parentPath == null ? TEMPORARY_DIRECTORY : parentPath,
                         TEMPORARY_FILE_PREFIX, sanitize(fileNameSuffix)
                     )
                     .toAbsolutePath()
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * @param path directory structure to create
     */
    public static void mkdir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * @param path to be touched (created, opened for writing and closed)
     */
    public static void touch(Path path) {
        try {
            IOUtil.closeQuietly(
                Files.newOutputStream(path, StandardOpenOption.CREATE)
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * @param path to be truncated (created, truncated and closed)
     */
    public static void truncate(Path path) {
        try {
            IOUtil.closeQuietly(
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * Tries to hardlink the file if possible (when both paths are located on the same filesystem that supports hardlinks and the target does not exist),
     * otherwise reverts to copying the file contents
     */
    public static void linkOrCopy(Path from, Path to) {
        if (!Files.exists(to)) {
            try {
                link(from, to);

                return;
            } catch (Exception e) {
                // log exception and revert to copy
                LOGGER.trace(e.getMessage(), e);
            }
        }

        try {
            final Path toTemporaryPath = to.getParent()
                                           .resolve(
                                               to.getFileName() + TEMPORARY_FILE_SUFFIX
                                           );

            Files.copy(from, toTemporaryPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(toTemporaryPath, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private static void link(Path from, Path to) throws IOException {
        Files.createLink(to, from);
    }

    /**
     * Tries to delete files if they exist, only logging errors when operation fails. Mostly to be used in finally blocks on temporary files. Directories are
     * only deleted if they are empty!
     */
    public static void deleteQuietly(Path... paths) {
        for (Path path : paths) {
            deleteQuietly(path);
        }
    }

    /**
     * Tries to delete file if it exists, only logging errors when operation fails. Mostly to be used in finally blocks on temporary files. Directories are only
     * deleted if they are empty!
     */
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

    /**
     * Removes the directory along with contents. Can only be used for temporary directory removal.
     */
    public static void deleteRecursively(Path rootDirectory) {
        if (rootDirectory == null) {
            return;
        }

        if (!isParentOf(TEMPORARY_DIRECTORY, rootDirectory)) {
            throw new UnexpectedException("Recursive directory removal, should only be used for temporary directories.");
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

    /**
     * @return file name with all possibly whitespaces or disallowed characters replaced with underscore ('_')
     */
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

    /**
     * @return size of the file
     */
    public static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    /**
     * @return true, if path is subdirectory of parentPath
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isParentOf(Path parentPath, Path path) {
        return path.toAbsolutePath()
                   .normalize()
                   .startsWith(
                       parentPath
                           .toAbsolutePath()
                           .normalize()
                   );
    }

    public static class TemporaryFileTracker {
        private final ReentrantLock stacktracesLock = new ReentrantLock();

        private final Map<String, UnexpectedException> stacktraces = new HashMap<>();

        private final ReentrantLock reachablePathsLock = new ReentrantLock();

        private final Map<Path, Boolean> reachablePaths = new WeakHashMap<>();

        private TemporaryFileTracker() {
            // NOP
        }

        public void log() {
            final Map<Path, UnexpectedException> unreachablePaths = new HashMap<>();

            stacktracesLock.lock();
            try {
                final Iterator<Entry<String, UnexpectedException>> iterator = stacktraces.entrySet().iterator();
                while (iterator.hasNext()) {
                    final Entry<String, UnexpectedException> entry = iterator.next();

                    final Path path = Paths.get(entry.getKey());
                    reachablePathsLock.lock();
                    try {
                        if (reachablePaths.containsKey(path)) {
                            continue;
                        }
                    } finally {
                        reachablePathsLock.unlock();
                    }

                    iterator.remove();

                    if (Files.exists(path)) {
                        unreachablePaths.put(path, entry.getValue());
                    }
                }
            } finally {
                stacktracesLock.unlock();
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
            stacktracesLock.lock();
            try {
                stacktraces.put(absolutePathAsString, stacktrace);
            } finally {
                stacktracesLock.unlock();
            }

            reachablePathsLock.lock();
            try {
                reachablePaths.put(absolutePath, Boolean.TRUE);
            } finally {
                reachablePathsLock.unlock();
            }

            return absolutePath;
        }
    }
}
