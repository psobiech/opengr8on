package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryShutter extends MqttDiscovery {
    @JsonProperty("position_topic")
    private final String positionStateTopic;

    @JsonProperty("set_position_topic")
    private final String setPositionTopic;

    @JsonProperty("set_position_template")
    private final String setPositionTemplate;

    @JsonProperty("state_open")
    private final String stateOpen = ShutterStateEnum.OPEN.name();

    @JsonProperty("state_opening")
    private final String stateOpening = ShutterStateEnum.OPENING.name();

    @JsonProperty("state_closing")
    private final String stateClosing = ShutterStateEnum.CLOSING.name();

    @JsonProperty("state_closed")
    private final String stateClosed = ShutterStateEnum.CLOSE.name();

    @JsonProperty("state_stopped")
    private final String stateStopped = ShutterStateEnum.STOP.name();

    public MqttDiscoveryShutter(
            String name, String uniqueId,
            String rootTopic, String availabilityTopic, String commandTopic, String stateTopic, String positionStateTopic, String setPositionTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate, String setPositionTemplate,
            MqttDiscoveryDevice device
    ) {
        super(
                name, uniqueId,
                rootTopic, availabilityTopic, commandTopic, stateTopic,
                deviceClass, unitOfMeasurement,
                schema, valueTemplate,
                device, MqttDiscoveryOrigin.INSTANCE
        );

        this.positionStateTopic = positionStateTopic;
        this.setPositionTopic = setPositionTopic;

        this.setPositionTemplate = setPositionTemplate;
    }

    public String getPositionStateTopic() {
        return getAbsoluteTopic(getRootTopic(), positionStateTopic);
    }

    public String getSetPositionTopic() {
        return getAbsoluteTopic(getRootTopic(), setPositionTopic);
    }

    public String getSetPositionTemplate() {
        return setPositionTemplate;
    }

    public String getStateOpen() {
        return stateOpen;
    }

    public String getStateOpening() {
        return stateOpening;
    }

    public String getStateClosing() {
        return stateClosing;
    }

    public String getStateClosed() {
        return stateClosed;
    }

    public String getStateStopped() {
        return stateStopped;
    }

    public enum ShutterStateEnum {
        OPEN, OPENING,
        CLOSE, CLOSING,
        STOP,
        //
        UNKNOWN,
        //
        ;

        public static ShutterStateEnum parse(String stateAsString) {
            if (stateAsString == null) {
                return UNKNOWN;
            }

            switch (stateAsString.toUpperCase()) {
                case "OPEN":
                    return OPEN;
                case "OPENING":
                    return OPENING;
                case "CLOSE":
                    return CLOSE;
                case "CLOSING":
                    return CLOSING;
                case "STOP":
                    return STOP;
            }

            return UNKNOWN;
        }
    }
}
