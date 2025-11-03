package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttBrightnessState extends MqttColorState {
    public static final String BRIGHTNESS_KEY = "brightness";

    @JsonProperty(BRIGHTNESS_KEY)
    private final Integer brightness;

    public MqttBrightnessState(
            int brightness
    ) {
        super(ColorMode.BRIGHTNESS, brightness > OFF_VALUE);

        this.brightness = brightness;
    }

    protected MqttBrightnessState(
            ColorMode colorMode, boolean isOn
    ) {
        super(colorMode, isOn);

        this.brightness = null;
    }

    @JsonCreator
    public MqttBrightnessState(StateEnum state, String colorMode, Integer brightness) {
        super(state, colorMode);

        this.brightness = brightness;
    }

    public Optional<Integer> getBrightness() {
        return Optional.ofNullable(brightness);
    }
}
