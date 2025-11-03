package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttState extends MqttJson {
    public static final int OFF_VALUE = 0;

    public static final String STATE_KEY = "state";

    @JsonProperty(STATE_KEY)
    private final StateEnum state;

    public MqttState(
            int value
    ) {
        state = value != OFF_VALUE ? StateEnum.ON : StateEnum.OFF;
    }

    public MqttState(
            boolean isOn
    ) {
        state = isOn ? StateEnum.ON : StateEnum.OFF;
    }

    @JsonCreator
    protected MqttState(StateEnum state) {
        this.state = state;
    }

    public boolean isOn() {
        return getState() == StateEnum.ON;
    }

    public StateEnum getState() {
        return Objects.requireNonNullElse(state, StateEnum.OFF);
    }

    public enum StateEnum {
        ON,
        OFF,
        //
        ;
    }

}
