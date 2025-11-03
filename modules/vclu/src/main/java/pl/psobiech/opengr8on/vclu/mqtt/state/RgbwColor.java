package pl.psobiech.opengr8on.vclu.mqtt.state;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RgbwColor {
    public static final String RED_KEY = "r";

    public static final String GREEN_KEY = "g";

    public static final String BLUE_KEY = "b";

    public static final String WHITE_KEY = "w";

    @JsonProperty(RED_KEY)
    private final Integer red;

    @JsonProperty(GREEN_KEY)
    private final Integer green;

    @JsonProperty(BLUE_KEY)
    private final Integer blue;

    @JsonProperty(WHITE_KEY)
    private final Integer white;

    public RgbwColor(Integer red, Integer green, Integer blue, Integer white) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.white = white;
    }

    public Optional<Integer> getRed() {
        return Optional.ofNullable(red);
    }

    public Optional<Integer> getGreen() {
        return Optional.ofNullable(green);
    }

    public Optional<Integer> getBlue() {
        return Optional.ofNullable(blue);
    }

    public Optional<Integer> getWhite() {
        return Optional.ofNullable(white);
    }
}
