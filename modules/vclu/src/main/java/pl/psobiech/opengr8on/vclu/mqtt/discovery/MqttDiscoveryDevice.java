package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Module;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryDevice {
    private static final String GRENTON = "Grenton";

    @JsonProperty("identifiers")
    private final String identifier;

    private final String name;

    private final String manufacturer;

    @JsonProperty("serial_number")
    private final String serialNumber;

    @JsonProperty("sw_version")
    private final String softwareVersion;

    @JsonProperty("hw_version")
    private final String hardwareVersion;

    @JsonProperty("via_device")
    private final String parentIdentifier;

    public MqttDiscoveryDevice(SpecificObject clu) {
        this(
                clu.getNameOnCLU(), clu.getName(), GRENTON,
                clu.getSerialNumber(),
                join("-", clu.getFirmwareType(), clu.getFirmwareVersion()),
                join("-", clu.getHardwareType(), clu.getHardwareVersion()),
                null
        );
    }

    public MqttDiscoveryDevice(SpecificObject object, Module module, SpecificObject parentObject) {
        this(
                object.getNameOnCLU(), "%s->%s".formatted(parentObject.getName(), object.getName()), GRENTON,
                object.getSerialNumber(),
                join(".", object.getFirmwareType(), object.getFirmwareVersion(), module.getModuleTypeFirmware(), module.getModuleVersion()),
                join(".", object.getHardwareType(), object.getHardwareVersion(), module.getModuleClass()),
                parentObject.getNameOnCLU()
        );
    }

    private static String join(String delimiter, Serializable... parts) {
        return Arrays.stream(parts)
                     .filter(Objects::nonNull)
                     .map(String::valueOf)
                     .map(StringUtils::stripToNull)
                     .filter(Objects::nonNull)
                     .collect(Collectors.joining(delimiter));
    }

    public MqttDiscoveryDevice(SpecificObject object, SpecificObject parentObject) {
        this(
                object.getNameOnCLU(), "%s->%s".formatted(parentObject.getName(), object.getName()), GRENTON,
                object.getSerialNumber(),
                join("-", object.getFirmwareType(), object.getFirmwareVersion()),
                join("-", object.getHardwareType(), object.getHardwareVersion()),
                parentObject.getNameOnCLU()
        );
    }

    @JsonCreator
    public MqttDiscoveryDevice(
            String identifier,
            String name,
            String manufacturer, String serialNumber,
            String softwareVersion, String hardwareVersion,
            String parentIdentifier
    ) {
        this.identifier = identifier;
        this.name = name;
        this.manufacturer = manufacturer;
        this.serialNumber = serialNumber;
        this.softwareVersion = softwareVersion;
        this.hardwareVersion = hardwareVersion;
        this.parentIdentifier = parentIdentifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public String getHardwareVersion() {
        return hardwareVersion;
    }

    public String getParentIdentifier() {
        return parentIdentifier;
    }
}
