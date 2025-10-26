package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryShutter extends MqttDiscovery {
    @JsonProperty("position_topic")
    private final String positionTopic;

    @JsonProperty("set_position_topic")
    private final String setPositionTopic;

    @JsonProperty("set_position_template")
    private final String setPositionTemplate;

    @JsonProperty("state_open")
    private final String stateOpen;

    @JsonProperty("state_closed")
    private final String stateClosed;

    public MqttDiscoveryShutter(
            String name, String uniqueId,
            String rootTopic, String commandTopic, String stateTopic, String positionTopic, String setPositionTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate, String setPositionTemplate, String stateOpen, String stateClosed,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, commandTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, valueTemplate,
                device, new MqttDiscoveryOrigin()
        );

        this.positionTopic = positionTopic;
        this.setPositionTopic = setPositionTopic;

        this.setPositionTemplate = setPositionTemplate;
        this.stateOpen = stateOpen;
        this.stateClosed = stateClosed;
    }
}
