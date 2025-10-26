package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryLight extends MqttDiscovery {
    @JsonProperty("supported_color_modes")
    private final Set<String> supportedColorModes;

    public MqttDiscoveryLight(
            String name, String uniqueId,
            String rootTopic, String commandTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate, Set<String> supportedColorModes,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, commandTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, valueTemplate,
                device, new MqttDiscoveryOrigin()
        );

        this.supportedColorModes = supportedColorModes;
    }

    public Set<String> getSupportedColorModes() {
        return supportedColorModes;
    }
}
