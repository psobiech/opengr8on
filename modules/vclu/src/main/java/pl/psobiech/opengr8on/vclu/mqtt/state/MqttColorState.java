package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttColorState extends MqttState {
    public static final int MAX_VALUE = 255;

    public static final String COLOR_MODE_KEY = "color_mode";

    @JsonProperty(COLOR_MODE_KEY)
    private final String colorMode;

    public MqttColorState(
            ColorMode colorMode, boolean isOn
    ) {
        super(isOn);

        this.colorMode = colorMode.key();
    }

    @JsonCreator
    protected MqttColorState(StateEnum state, String colorMode) {
        super(state);

        this.colorMode = colorMode;
    }

    public String getColorMode() {
        return colorMode;
    }

    public enum ColorMode {
        RGBW("rgbw"),
        BRIGHTNESS("brightness"),
        ON_OFF("onoff"),
        //
        ;

        private final String key;

        ColorMode(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }
}
