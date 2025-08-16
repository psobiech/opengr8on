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

package pl.psobiech.opengr8on.xml.omp;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.xml.omp.properties.ProjectProperties;
import pl.psobiech.opengr8on.xml.omp.properties.ProjectPropertiesCipherKey;
import pl.psobiech.opengr8on.xml.omp.properties.ProjectPropertiesWrapper;
import pl.psobiech.opengr8on.xml.omp.system.TreeObjectNode;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.IO;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class OmpReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OmpReader.class);

    private static final String PROPERTIES_XML_FILE_NAME = "properties.xml";

    private static final String SYSTEM_XML_FILE_NAME = "system.xml";

    private static final Pattern ID_TAG_PATTERN = Pattern.compile("<\\s*(/)?\\s*id\\s*>");

    private OmpReader() {
        // NOP
    }

    public static Map<Long, SpecificObject> readAllObjects(Path projectFile) {
        try (var zipFile = new ZipFile(projectFile.toFile())) {
            final var propertiesXmlEntry = zipFile.getEntry(SYSTEM_XML_FILE_NAME);
            final Path temporaryFile = fixJacksonXmlDuplicateIdentifiers(zipFile.getInputStream(propertiesXmlEntry));
            try {
                try (var inputStream = Files.newInputStream(temporaryFile)) {
                    return extractAllObjects(ObjectMapperFactory.XML.readValue(inputStream, TreeObjectRootType.INSTANCE));
                }
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
        } catch (IOException e) {
            throw new UnexpectedException("Cannot load omp project from file: " + projectFile, e);
        }
    }

    /**
     * Workaround for <a href="https://github.com/FasterXML/jackson-dataformat-xml/issues/65">https://github.com/FasterXML/jackson-dataformat-xml/issues/65</a>
     */
    private static Path fixJacksonXmlDuplicateIdentifiers(InputStream inputStream) throws IOException {
        final Path temporaryFile = Files.createTempFile(null, null);
        try (var reader = new BufferedReader(new InputStreamReader(inputStream)); var outputStream = new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(temporaryFile)))) {
            String line = reader.readLine();
            do {
                line = ID_TAG_PATTERN.matcher(line).replaceAll("<$1_id>");

                outputStream.write(line);
                outputStream.write('\n');

                line = reader.readLine();
            } while (line != null);
        }

        return temporaryFile;
    }

    private static Map<Long, SpecificObject> extractAllObjects(List<TreeObjectNode> nodes) {
        var objectMap = new TreeMap<Long, SpecificObject>();
        for (TreeObjectNode object : nodes) {
            objectMap.putAll(extractAllObjects(object));
        }

        return objectMap;
    }

    private static Map<Long, SpecificObject> extractAllObjects(TreeObjectNode nodes) {
        var objectMap = new HashMap<Long, SpecificObject>();
        for (TreeObjectNode child : nodes.getChildren()) {
            objectMap.putAll(extractAllObjects(child));
        }

        objectMap.putAll(extractAllObjects(nodes.getSpecificObject()));

        return objectMap;
    }

    private static Map<Long, SpecificObject> extractAllObjects(SpecificObject specificObject) {
        if (specificObject == null) {
            return Collections.emptyMap();
        }

        var objectMap = new HashMap<Long, SpecificObject>();
        specificObject.findId().ifPresent(id -> objectMap.put(id, specificObject));

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Extracted specific {}: {}", specificObject.findId().map(ignored -> "object").orElse("object reference"), specificObject);
        }

        for (IO io : specificObject.getIosList()) {
            objectMap.putAll(extractAllObjects(io));
        }

        objectMap.putAll(extractAllObjects(specificObject.getClu()));

        return objectMap;
    }

    public static CipherKey readProjectCipherKey(Path projectFile) {
        try (ZipFile zipFile = new ZipFile(projectFile.toFile())) {
            final ZipEntry propertiesXmlEntry = zipFile.getEntry(PROPERTIES_XML_FILE_NAME);
            try (InputStream inputStream = zipFile.getInputStream(propertiesXmlEntry)) {
                final ProjectPropertiesWrapper projectPropertiesWrapper = ObjectMapperFactory.XML.readValue(inputStream, ProjectPropertiesWrapper.class);
                final ProjectProperties projectProperties = projectPropertiesWrapper.getProjectProperties();
                final ProjectPropertiesCipherKey projectCipherKey = projectProperties.getProjectCipherKey();

                final CipherKey cipherKey = new CipherKey(Base64.decodeBase64(projectCipherKey.getKeyBytes().getValue()), Base64.decodeBase64(projectCipherKey.getIvBytes().getValue()));

                LOGGER.trace("Loaded project key: {}", cipherKey);

                return cipherKey;
            }
        } catch (IOException e) {
            throw new UnexpectedException("Cannot load omp project from file: " + projectFile, e);
        }
    }

    private static class TreeObjectRootType extends TypeReference<List<TreeObjectNode>> {
        public final static TreeObjectRootType INSTANCE = new TreeObjectRootType();

        @Override
        public Type getType() {
            return super.getType();
        }
    }
}
