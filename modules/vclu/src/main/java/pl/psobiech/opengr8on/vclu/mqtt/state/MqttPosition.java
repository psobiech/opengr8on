package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttPosition extends MqttJson {
    public static final String POSITION_KEY = "position";

    @JsonProperty(POSITION_KEY)
    private final Integer position;

    @JsonCreator
    public MqttPosition(Integer position) {
        this.position = position;
    }

    public Optional<Integer> getPosition() {
        return Optional.ofNullable(position);
    }
}
