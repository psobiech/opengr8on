package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoverySensor {
    @JsonProperty("~")
    private final String rootTopic;

    private final String name;

    @JsonProperty("unique_id")
    private final String uniqueId;

    @JsonProperty("command_topic")
    private final String commandTopic;

    @JsonProperty("state_topic")
    private final String stateTopic;

    @JsonProperty("device_class")
    private final String deviceClass;

    @JsonProperty("unit_of_measurement")
    private final String unitOfMeasurement;

    @JsonProperty("value_template")
    private final String valueTemplate;

    @JsonProperty("device")
    private final MqttDiscoveryDevice device;

    @JsonProperty("origin")
    private final MqttDiscoveryOrigin origin;

    public MqttDiscoverySensor(String name, String uniqueId, String rootTopic, String commandTopic, String stateTopic, String deviceClass, String unitOfMeasurement, String valueTemplate, MqttDiscoveryDevice device, MqttDiscoveryOrigin origin) {
        this.name = name;
        this.uniqueId = uniqueId;
        this.deviceClass = deviceClass;
        this.unitOfMeasurement = unitOfMeasurement;
        this.valueTemplate = valueTemplate;

        this.rootTopic = rootTopic;
        this.commandTopic = commandTopic;
        this.stateTopic = stateTopic;

        this.device = device;
        this.origin = origin;
    }

    public String getRootTopic() {
        return rootTopic;
    }

    public String getName() {
        return name;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getCommandTopic() {
        return commandTopic;
    }

    public String getStateTopic() {
        return stateTopic;
    }

    public String getDeviceClass() {
        return deviceClass;
    }

    public String getUnitOfMeasurement() {
        return unitOfMeasurement;
    }

    public String getValueTemplate() {
        return valueTemplate;
    }

    public MqttDiscoveryDevice getDevice() {
        return device;
    }

    public MqttDiscoveryOrigin getOrigin() {
        return origin;
    }
}
