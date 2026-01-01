package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.vclu.mqtt.MqttJson;

import static pl.psobiech.opengr8on.vclu.system.objects.remoteclu.devices.RemoteCLUDevice.availabilityTopic;
import static pl.psobiech.opengr8on.vclu.util.MqttUtil.absoluteTopic;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscovery extends MqttJson {
    private final String name;

    @JsonProperty("unique_id")
    private final String uniqueId;

    @JsonProperty("~")
    private final String rootTopic;

    @JsonProperty("availability_topic")
    private final String availabilityTopic = availabilityTopic();

    @SuppressWarnings("unused")
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

    public MqttDiscovery(MqttDiscoveryDevice discoveryDevice) {
        this(
                null, null,
                null, null, null,
                null, null,
                null,
                null,
                discoveryDevice,
                MqttDiscoveryOrigin.INSTANCE
        );
    }

    @JsonCreator
    public MqttDiscovery(
            String name, String uniqueId,
            String rootTopic, String setStateTopic, String stateTopic,
            String deviceClass, String unitOfMeasurement,
            String schema, String valueTemplate,
            MqttDiscoveryDevice device, MqttDiscoveryOrigin origin
    ) {
        this.name = name;
        this.uniqueId = uniqueId;

        this.rootTopic = rootTopic;
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

    public String getRootTopic() {
        return rootTopic;
    }

    public String getAvailabilityTopic() {
        return absoluteTopic(getRootTopic(), availabilityTopic);
    }

    public String getAvailabilityMode() {
        return availabilityMode;
    }

    public String getSetStateTopic() {
        return absoluteTopic(getRootTopic(), setStateTopic);
    }

    public String getStateTopic() {
        return absoluteTopic(getRootTopic(), stateTopic);
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
