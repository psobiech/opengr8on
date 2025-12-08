package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryNumericFloat extends MqttDiscovery {
    @JsonProperty("min")
    private final Float max;

    @JsonProperty("max")
    private final Float min;

    public MqttDiscoveryNumericFloat(
            String name, String uniqueId,
            String rootTopic, String setStateTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, Float max, Float min,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, setStateTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, "{{ value | float }}",
                device,
                MqttDiscoveryOrigin.INSTANCE
        );

        this.max = max;
        this.min = min;
    }
}
