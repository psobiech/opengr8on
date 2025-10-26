package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryButton extends MqttDiscovery {
    @JsonProperty("event_types")
    private final Set<String> eventTypes;

    public MqttDiscoveryButton(
            String name, String uniqueId,
            String rootTopic, String commandTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate, Set<String> eventTypes,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, commandTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, valueTemplate,
                device, new MqttDiscoveryOrigin()
        );

        this.eventTypes = eventTypes;
    }

    public Set<String> getEventTypes() {
        return eventTypes;
    }
}
