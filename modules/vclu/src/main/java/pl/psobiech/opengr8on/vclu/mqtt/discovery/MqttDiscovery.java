package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscovery extends MqttJson {
    private final String name;

    @JsonProperty("unique_id")
    private final String uniqueId;

    @JsonProperty("~")
    private final String rootTopic;

    @JsonProperty("availability_topic")
    private final String availabilityTopic;

    @JsonProperty("availability_mode")
    private final String availabilityMode = "latest";

    @JsonProperty("command_topic")
    private final String setStateTopic;

    @JsonProperty("state_topic")
    private final String stateTopic;

    @JsonProperty("device_class")
    private final String deviceClass;

    @JsonProperty("unit_of_measurement")
    private final String unitOfMeasurement;

    @JsonProperty("schema")
    private final String schema;

    @JsonProperty("value_template")
    private final String valueTemplate;

    @JsonProperty("device")
    private final MqttDiscoveryDevice device;

    @JsonProperty("origin")
    private final MqttDiscoveryOrigin origin;

    public MqttDiscovery(
            String name, String uniqueId,
            String rootTopic, String availabilityTopic, String setStateTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate,
            MqttDiscoveryDevice device, MqttDiscoveryOrigin origin
    ) {
        this.name = name;
        this.uniqueId = uniqueId;

        this.rootTopic = rootTopic;
        this.availabilityTopic = StringUtils.stripToNull(availabilityTopic);
        this.setStateTopic = StringUtils.stripToNull(setStateTopic);
        this.stateTopic = StringUtils.stripToNull(stateTopic);

        this.deviceClass = StringUtils.stripToNull(deviceClass);
        this.unitOfMeasurement = StringUtils.stripToNull(unitOfMeasurement);

        this.schema = schema;
        this.valueTemplate = valueTemplate;

        this.device = device;
        this.origin = origin;
    }

    public String getName() {
        return name;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public static String getAbsoluteTopic(String rootTopic, String topic) {
        if (rootTopic != null && topic != null && topic.startsWith("~/")) {
            return rootTopic + topic.substring(1);
        }

        return topic;
    }

    public String getRootTopic() {
        return rootTopic;
    }

    public String getAvailabilityTopic() {
        return getAbsoluteTopic(getRootTopic(), availabilityTopic);
    }

    public String getSetStateTopic() {
        return getAbsoluteTopic(getRootTopic(), setStateTopic);
    }

    public String getStateTopic() {
        return getAbsoluteTopic(getRootTopic(), stateTopic);
    }

    public String getDeviceClass() {
        return deviceClass;
    }

    public String getUnitOfMeasurement() {
        return unitOfMeasurement;
    }

    public String getSchema() {
        return schema;
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
