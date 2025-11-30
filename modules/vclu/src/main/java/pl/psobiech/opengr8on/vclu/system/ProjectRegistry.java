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

package pl.psobiech.opengr8on.vclu.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.xml.omp.OmpReader;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Module;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObjectType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ProjectRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectRegistry.class);

    private static final String PROJECT_FILE = "project.omp";

    private final Map<Long, SpecificObject> referenceObjectMap;

    private final Map<Long, Module> referenceModuleMap;

    private final Map<String, SpecificObject> cluMap;

    private final Map<String, Set<SpecificObject>> cluObjectsMap;

    public ProjectRegistry(Path rootDirectory) {
        final Path projectOmpPath = rootDirectory.getParent().resolve(PROJECT_FILE);
        if (!Files.exists(projectOmpPath)) {
            this.referenceObjectMap = Collections.emptyMap();
            this.cluMap = Collections.emptyMap();
            this.cluObjectsMap = Collections.emptyMap();
            this.referenceModuleMap = Collections.emptyMap();

            return;
        }

        this.referenceObjectMap = Collections.unmodifiableMap(OmpReader.readAllObjects(projectOmpPath));
        final var clus = new HashMap<String, SpecificObject>();
        final var cluObjects = new HashMap<String, Set<SpecificObject>>();
        final var modules = new HashMap<Long, Module>();
        for (SpecificObject object : referenceObjectMap.values()) {
            if (object.getType() == SpecificObjectType.CLU) {
                clus.put(object.getNameOnCLU(), object);
            }

            final Module module = object.getModule();
            if (module != null) {
                if (module.getId() != null) {
                    modules.put(module.getId(), module);
                }
            }

            SpecificObject clu = object.getClu();
            if (clu != null && clu.getReference() != null) {
                clu = referenceObjectMap.get(clu.getReference());
            }

            if (clu == null) {
                continue;
            }

            final String cluDeviceId = clu.getNameOnCLU();

            cluObjects.computeIfAbsent(cluDeviceId, ignored -> new HashSet<>())
                      .add(object);
        }

        final var cluObjectsUnmodifiable = new HashMap<String, Set<SpecificObject>>();
        for (Map.Entry<String, Set<SpecificObject>> entry : cluObjects.entrySet()) {
            cluObjectsUnmodifiable.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }

        cluMap = Collections.unmodifiableMap(clus);
        cluObjectsMap = Collections.unmodifiableMap(cluObjectsUnmodifiable);
        referenceModuleMap = Collections.unmodifiableMap(modules);
    }

    public boolean isEmpty() {
        return referenceObjectMap.isEmpty();
    }

    public Optional<SpecificObject> byReference(Long reference) {
        if (reference == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                referenceObjectMap.get(reference)
        );
    }

    public Optional<Module> moduleByReference(Long reference) {
        if (reference == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                referenceModuleMap.get(reference)
        );
    }

    public SpecificObject cluByName(String cluName) {
        if (cluName == null) {
            return null;
        }

        return cluMap.get(cluName);
    }

    public Set<SpecificObject> byCluName(String cluName) {
        if (cluName == null) {
            return Collections.emptySet();
        }

        return cluObjectsMap.getOrDefault(cluName, Collections.emptySet());
    }

    public Optional<ObjectId> getObjectIdParts(String objectId) {
        final String[] parts = objectId.split("->", 3);
        if (parts.length == 2) {
            return Optional.of(new ObjectId(parts[0], parts[1]));
        }

        return Optional.empty();
    }

    public record ObjectId(String cluName, String objectName) {
        // NOP
    }
}
