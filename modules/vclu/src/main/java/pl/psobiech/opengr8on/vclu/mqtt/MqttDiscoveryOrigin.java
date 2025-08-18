package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryOrigin {
    private final String name;

    @JsonProperty("sw_version")
    private final String softwareVersion;

    @JsonProperty("url")
    private final String url;

    @JsonCreator
    public MqttDiscoveryOrigin(String name, String softwareVersion, String url) {
        this.name = name;
        this.softwareVersion = softwareVersion;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public String getUrl() {
        return url;
    }
}
