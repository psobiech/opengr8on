package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryNumberFloat extends MqttDiscovery {
    @JsonProperty("data_type")
    private final String dataType = "float32";

    @JsonProperty("min")
    private final Float min;

    @JsonProperty("max")
    private final Float max;

    public MqttDiscoveryNumberFloat(
            String name, String uniqueId,
            String rootTopic, String setStateTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            Float min, Float max,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, setStateTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                null, "{{ value | float }}",
                device,
                MqttDiscoveryOrigin.INSTANCE
        );

        this.min = min;
        this.max = max;
    }

    public String getDataType() {
        return dataType;
    }

    public Float getMin() {
        return min;
    }

    public Float getMax() {
        return max;
    }
}
