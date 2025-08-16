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

package pl.psobiech.opengr8on.xml.interfaces;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;

public class InterfaceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterfaceRegistry.class);

    private static final String SEPARATOR = ":";

    private static final int HARDWARE_TYPE_LENGTH = 16;

    private static final int HARDWARE_VERSION_LENGTH = 16;

    private static final int FIRMWARE_TYPE_LENGTH = 8;

    private static final int FIRMWARE_VERSION_LENGTH = 4;

    private final Map<String, CLU> clus;

    private final Map<String, CLUModule> modules;

    private final Map<String, Map<String, CLUObject>> objects;

    public InterfaceRegistry(Path rootPath) {
        final List<Path> cluInterfaceFiles = new ArrayList<>();
        final List<Path> moduleInterfaceFiles = new ArrayList<>();
        final List<Path> objectInterfaceFiles = new ArrayList<>();

        try {
            Files.walkFileTree(
                    rootPath,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            final String fileName = String.valueOf(file.getFileName());
                            if (fileName.startsWith("clu_")) {
                                cluInterfaceFiles.add(file);
                            } else if (fileName.startsWith("module_")) {
                                moduleInterfaceFiles.add(file);
                            } else if (fileName.startsWith("object_")) {
                                objectInterfaceFiles.add(file);
                            }

                            return super.visitFile(file, attrs);
                        }
                    }
            );
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        final Map<String, CLU> clus = new TreeMap<>(String::compareTo);
        final Map<String, CLUModule> modules = new TreeMap<>(String::compareTo);
        final Map<String, Map<String, CLUObject>> objects = new TreeMap<>(String::compareTo);

        final ObjectReader cluDefinitionReader = ObjectMapperFactory.XML.readerFor(CLU.class);
        for (Path cluInterfaceFile : cluInterfaceFiles) {
            try {
                final CLU clu = cluDefinitionReader.readValue(cluInterfaceFile.toFile());
                final String cluKey = createCluKey(clu);
                LOGGER.trace("Loaded: " + cluKey);

                clus.put(cluKey, clu);
            } catch (IOException e) {
                throw new UnexpectedException("Error loading " + cluInterfaceFile, e);
            }
        }

        final ObjectReader moduleDefinitionReader = ObjectMapperFactory.XML.readerFor(CLUModule.class);
        for (Path moduleInterfaceFile : moduleInterfaceFiles) {
            try {
                final CLUModule module = moduleDefinitionReader.readValue(moduleInterfaceFile.toFile());
                final String moduleKey = createModuleKey(module);
                LOGGER.trace("Loaded: " + moduleKey);

                modules.put(moduleKey, module);
            } catch (IOException e) {
                throw new UnexpectedException("Error loading " + moduleInterfaceFile, e);
            }
        }

        final ObjectReader objectDefinitionReader = ObjectMapperFactory.XML.readerFor(CLUObject.class);
        for (Path objectInterfaceFile : objectInterfaceFiles) {
            try {
                final CLUObject object = objectDefinitionReader.readValue(objectInterfaceFile.toFile());
                final String name = object.getName();
                final String version = createObjectVersionKey(object.getVersion());

                objects.computeIfAbsent(name, ignored -> new TreeMap<>(String::compareTo))
                        .put(version, object);

                LOGGER.trace("Loaded: " + name + SEPARATOR + version);
            } catch (IOException e) {
                throw new UnexpectedException("Error loading " + objectInterfaceFile, e);
            }
        }

        int objectNumbers = 0;
        final Set<String> objectKeys = new HashSet<>(objects.keySet());
        for (String objectKey : objectKeys) {
            final Map<String, CLUObject> objectVersions = objects.get(objectKey);
            objectNumbers += objectVersions.size();

            objects.put(objectKey, Collections.unmodifiableMap(objectVersions));
        }

        this.clus = Collections.unmodifiableMap(clus);
        this.modules = Collections.unmodifiableMap(modules);
        this.objects = Collections.unmodifiableMap(objects);

        LOGGER.debug("Loaded Interfaces: clus: {}, modules: {}, objects: {}", this.clus.size(), this.modules.size(), objectNumbers);
    }

    private InterfaceRegistry() {
        this.clus = Collections.emptyMap();
        this.modules = Collections.emptyMap();
        this.objects = Collections.emptyMap();
    }

    public Optional<CLU> getCLU(int hardwareType, long hardwareVersion, int firmwareType, int firmwareVersion) {
        return Optional.ofNullable(
                clus.get(
                        createCluKey(hardwareType, hardwareVersion, firmwareType, firmwareVersion)
                )
        );
    }

    private static String createCluKey(CLU clu) {
        return createCluKey(
                HexUtil.asInt(clu.getHardwareType()), HexUtil.asLong(clu.getHardwareVersion()),
                HexUtil.asInt(clu.getFirmwareType()), HexUtil.asInt(clu.getFirmwareVersion())
        );
    }

    private static String createCluKey(
            int hardwareType, long hardwareVersion,
            int firmwareType, int firmwareVersion
    ) {
        return createKey(
                hardwareType & 0xFFFFFFFFL, hardwareVersion,
                firmwareType, firmwareVersion
        );
    }

    public Optional<CLUModule> getModule(long hardwareType, int firmwareType, int firmwareVersion) {
        return Optional.ofNullable(
                modules.get(
                        createModuleKey(
                                hardwareType,
                                firmwareType, firmwareVersion
                        )
                )
        );
    }

    private static String createModuleKey(CLUModule clu) {
        final ModuleFirmware firmware = clu.getFirmware();

        return createModuleKey(
                HexUtil.asLong(clu.getTypeId()),
                HexUtil.asInt(firmware.getTypeId()), HexUtil.asInt(firmware.getVersion())
        );
    }

    private static String createModuleKey(long hardwareType, int firmwareType, int firmwareVersion) {
        return "MOD" + SEPARATOR + createKey(
                hardwareType, 1,
                firmwareType, firmwareVersion
        );
    }

    private static String createKey(long hardwareType, long hardwareVersion, int firmwareType, int firmwareVersion) {
        return parse(hardwareType, HARDWARE_TYPE_LENGTH) + SEPARATOR + parse(hardwareVersion, HARDWARE_VERSION_LENGTH) + SEPARATOR
                + parse(firmwareType, FIRMWARE_TYPE_LENGTH) + SEPARATOR + parse(firmwareVersion, FIRMWARE_VERSION_LENGTH);
    }

    public Optional<CLUObject> getObject(String name, int version) {
        final Map<String, CLUObject> objectVersions = objects.get(name);
        if (objectVersions == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                objectVersions.get(createObjectVersionKey(version))
        );
    }

    private static String createObjectVersionKey(String version) {
        return createObjectVersionKey(HexUtil.asInt(version));
    }

    private static String createObjectVersionKey(int version) {
        return parse(version, 2);
    }

    private static String parse(long hexAsLong, int length) {
        return StringUtils.leftPad(HexUtil.asString(hexAsLong), length, '0');
    }
}
