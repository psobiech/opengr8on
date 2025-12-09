package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryNumberInteger extends MqttDiscovery {
    @JsonProperty("data_type")
    private final String dataType = "int64";

    @JsonProperty("min")
    private final Long min;

    @JsonProperty("max")
    private final Long max;

    public MqttDiscoveryNumberInteger(
            String name, String uniqueId,
            String rootTopic, String setStateTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            Long min, Long max,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, setStateTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                null, "{{ value | int }}",
                device,
                MqttDiscoveryOrigin.INSTANCE
        );

        this.min = min;
        this.max = max;
    }

    public String getDataType() {
        return dataType;
    }

    public Long getMin() {
        return min;
    }

    public Long getMax() {
        return max;
    }
}
