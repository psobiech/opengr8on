package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryButton extends MqttDiscovery {
    @JsonProperty("event_types")
    private final Set<String> eventTypes;

    public MqttDiscoveryButton(
            String name, String uniqueId,
            String rootTopic, String setStateTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate, Set<String> eventTypes,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, setStateTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, valueTemplate,
                device, MqttDiscoveryOrigin.INSTANCE
        );

        this.eventTypes = eventTypes;
    }

    public Set<String> getEventTypes() {
        return eventTypes;
    }
}
