package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryLight extends MqttDiscovery {
    @JsonProperty("supported_color_modes")
    private final Set<String> supportedColorModes;

    public MqttDiscoveryLight(
            String name, String uniqueId,
            String rootTopic, String availabilityTopic, String commandTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate, Set<String> supportedColorModes,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, availabilityTopic, commandTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, valueTemplate,
                device, MqttDiscoveryOrigin.INSTANCE
        );

        this.supportedColorModes = supportedColorModes;
    }

    public Set<String> getSupportedColorModes() {
        return supportedColorModes;
    }
}
