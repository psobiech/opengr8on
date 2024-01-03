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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;

public class OmpReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(OmpReader.class);

    private static final String PROPERTIES_XML_FILE_NAME = "properties.xml";

    private OmpReader() {
        // NOP
    }

    public static CipherKey readProjectCipherKey(Path projectFile) {
        try (ZipFile zipFile = new ZipFile(projectFile.toFile())) {
            final ZipEntry propertiesXmlEntry = zipFile.getEntry(PROPERTIES_XML_FILE_NAME);
            try (InputStream inputStream = zipFile.getInputStream(propertiesXmlEntry)) {
                final ObjectReader propertiesReader = ObjectMapperFactory.XML.readerFor(ProjectPropertiesWrapper.class);
                final ProjectPropertiesWrapper projectPropertiesWrapper = propertiesReader.readValue(inputStream, ProjectPropertiesWrapper.class);
                final ProjectProperties projectProperties = projectPropertiesWrapper.getProjectProperties();
                final ProjectPropertiesCipherKey projectCipherKey = projectProperties.getProjectCipherKey();

                final CipherKey cipherKey = new CipherKey(
                    Base64.decodeBase64(projectCipherKey.getKeyBytes().getValue()),
                    Base64.decodeBase64(projectCipherKey.getIvBytes().getValue())
                );

                LOGGER.debug("Loaded project key: {}", cipherKey);

                return cipherKey;
            }
        } catch (IOException e) {
            throw new UnexpectedException("Cannot load omp project from file: " + projectFile, e);
        }
    }
}
