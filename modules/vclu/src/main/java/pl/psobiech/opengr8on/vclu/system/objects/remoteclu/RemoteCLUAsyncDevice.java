package pl.psobiech.opengr8on.vclu.system.objects.remoteclu;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Event;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.util.*;
import java.util.stream.Collectors;

public interface RemoteCLUAsyncDevice {
    default Set<String> asyncHandlersInstalled(String uniqueId, SpecificObject virtualCluObject, SpecificObject clu, SpecificObject object) {
        return asyncHandlersInstalled(null, uniqueId, virtualCluObject, clu, object);
    }

    default Set<String> asyncHandlersInstalled(Logger logger, String uniqueId, SpecificObject virtualCluObject, SpecificObject clu, SpecificObject object) {
        if (virtualCluObject == null) {
            logger.warn("Current CLU is missing from the project file, please perform device discovery..");

            return Collections.emptySet();
        }

        final Map<String, Set<String>> changeEventCommands = object.getEvents().stream()
                                                                   .filter(RemoteCLUAsyncDevice::isChangeEvent)
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
            if (logger != null && logger.isInfoEnabled()) {
                logger.info("Async handler is missing for object: {} ({}) - Scheduled polling will still be used for this object. " +
                                    "Possible events to configure are: {}",
                            uniqueId, object.getName(),
                            changeEventCommands.keySet().stream()
                                               .map(s -> "%s = %s".formatted(s, expectedAsyncOnChangeCommand))
                                               .collect(Collectors.toSet())
                );
            }

            return Collections.emptySet();
        }

        return configuredAsyncHandlers;
    }

    private static boolean isChangeEvent(Event event) {
        final String eventNameLowercase = StringUtils.lowerCase(event.getName());

        return eventNameLowercase.endsWith("change") || eventNameLowercase.endsWith("click");
    }
}
