package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttRgbwState extends MqttBrightnessState {
    public static final String COLOR_KEY = "color";

    @JsonProperty(COLOR_KEY)
    private final RgbwColor color;

    public MqttRgbwState(int red, int green, int blue, int white) {
        super(MqttColorState.ColorMode.RGBW, (red + green + blue + white) > OFF_VALUE);

        this.color = new RgbwColor(red, green, blue, white);
    }

    @JsonCreator
    public MqttRgbwState(StateEnum state, String colorMode, Integer brightness, RgbwColor color) {
        super(state, colorMode, brightness);

        this.color = color;
    }

    public Optional<RgbwColor> getColor() {
        return Optional.ofNullable(color);
    }
}
