package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttEvent extends MqttJson {
    public static final String EVENT_TYPE_KEY = "event_type";

    @JsonProperty(EVENT_TYPE_KEY)
    private final String eventType;

    @JsonCreator
    public MqttEvent(String eventType) {
        this.eventType = eventType;
    }

    public String getEventType() {
        return eventType;
    }
}
