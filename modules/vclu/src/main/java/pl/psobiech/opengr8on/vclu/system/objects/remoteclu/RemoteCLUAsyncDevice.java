package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Event;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface RemoteCLUAsyncDevice {
    default boolean hasAsyncHandlersInstalled(String uniqueId, SpecificObject virtualCluObject, SpecificObject clu, SpecificObject object) {
        return hasAsyncHandlersInstalled(null, uniqueId, virtualCluObject, clu, object);
    }

    default boolean hasAsyncHandlersInstalled(Logger logger, String uniqueId, SpecificObject virtualCluObject, SpecificObject clu, SpecificObject object) {
        if (virtualCluObject == null) {
            logger.warn("Current CLU is missing from the project file, please perform device discovery..");

            return false;
        }

        final Map<String, Set<String>> changeEventCommands = object.getEvents().stream()
                                                                   .filter(event -> StringUtils.lowerCase(event.getName()).endsWith("change"))
                                                                   .collect(Collectors.toMap(
                                                                           Event::getName,
                                                                           event ->
                                                                                   event.getCommands()
                                                                                        .getCommandValues().stream()
                                                                                        .map(StringUtils::stripToNull)
                                                                                        .filter(Objects::nonNull)
                                                                                        .collect(Collectors.toSet())
                                                                   ));

        final String expectedAsyncOnChangeCommand = "%s->mqttOnValueChange(\"%s->%s\")".formatted(virtualCluObject.getName(), clu.getNameOnCLU(), object.getNameOnCLU());

        final Set<String> configuredAsyncHandlers = new HashSet<>();
        if (!changeEventCommands.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : changeEventCommands.entrySet()) {

                final String eventName = entry.getKey();
                final Set<String> eventCommands = entry.getValue();
                if (eventCommands.contains(expectedAsyncOnChangeCommand)) {
                    configuredAsyncHandlers.add(eventName);
                }
            }
        }

        if (configuredAsyncHandlers.isEmpty()) {
            if (logger != null && logger.isWarnEnabled()) {
                logger.warn("Async handler is missing for object: {} ({}) - Scheduled polling will still be used for this object. \n" +
                                    "Possible events to configure are: {}",
                            uniqueId, object.getName(),
                            changeEventCommands.keySet().stream()
                                               .map(s -> "%s = %s".formatted(s, expectedAsyncOnChangeCommand))
                                               .collect(Collectors.toSet())
                );
            }

            return false;
        }

        if (logger != null && logger.isInfoEnabled()) {
            logger.info("Detected proper async handlers installed for object: {} ({}) (handlers: {}) - Disabling automated polling in favor of async communication",
                        uniqueId, object.getName(),
                        configuredAsyncHandlers
            );
        }

        return true;
    }
}
