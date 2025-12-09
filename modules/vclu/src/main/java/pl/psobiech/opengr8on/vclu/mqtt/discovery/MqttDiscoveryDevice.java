package pl.psobiech.opengr8on.vclu.mqtt.discovery;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.Module;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryDevice {
    private static final String GRENTON = "Grenton";

    private final List<String> identifiers;

    private final String name;

    private final String manufacturer;

    private final String model;

    @JsonProperty("serial_number")
    private final String serialNumber;

    @JsonProperty("sw_version")
    private final String softwareVersion;

    @JsonProperty("hw_version")
    private final String hardwareVersion;

    @JsonProperty("connections")
    private final List<List<String>> connections;

    @JsonProperty("via_device")
    private final String parentIdentifier;

    public MqttDiscoveryDevice(SpecificObject object) {
        this(
                concat(object.getNameOnCLU(), object.getSerialNumber(), object.getMacAddress()), object.getName(),
                GRENTON, object.getType().name(),
                object.getSerialNumber(),
                join("-", object.getFirmwareType(), object.getFirmwareVersion()),
                join("-", object.getHardwareType(), object.getHardwareVersion()),
                Optional.ofNullable(object.getMacAddress()).map(mac -> List.of(List.of("mac", mac))).orElse(null),
                null
        );
    }

    public MqttDiscoveryDevice(SpecificObject object, Module module, SpecificObject parentObject) {
        this(
                concat(object.getNameOnCLU(), object.getSerialNumber(), object.getMacAddress()), object.getName(),
                GRENTON, object.getType().name(),
                object.getSerialNumber(),
                join(".", object.getFirmwareType(), object.getFirmwareVersion(), module.getModuleTypeFirmware(), module.getModuleVersion()),
                join(".", object.getHardwareType(), object.getHardwareVersion(), module.getModuleClass()),
                Optional.ofNullable(object.getMacAddress()).map(mac -> List.of(List.of("mac", mac))).orElse(null),
                parentObject.getNameOnCLU()
        );
    }

    public MqttDiscoveryDevice(SpecificObject object, SpecificObject parentObject) {
        this(
                concat(object.getNameOnCLU(), object.getSerialNumber(), object.getMacAddress()), object.getName(),
                GRENTON, object.getType().name(),
                object.getSerialNumber(),
                join("-", object.getFirmwareType(), object.getFirmwareVersion()),
                join("-", object.getHardwareType(), object.getHardwareVersion()),
                Optional.ofNullable(object.getMacAddress()).map(mac -> List.of(List.of("mac", mac))).orElse(null),
                parentObject.getNameOnCLU()
        );
    }

    private static String join(String delimiter, Serializable... parts) {
        return stream(parts)
                .collect(Collectors.joining(delimiter));
    }

    private static List<String> concat(Serializable... parts) {
        return stream(parts)
                .toList();
    }

    private static Stream<String> stream(Serializable... parts) {
        return Arrays.stream(parts)
                     .filter(Objects::nonNull)
                     .map(String::valueOf)
                     .map(StringUtils::stripToNull)
                     .filter(Objects::nonNull);
    }

    @JsonCreator
    public MqttDiscoveryDevice(
            List<String> identifiers, String name,
            String manufacturer,
            String model,
            String serialNumber,
            String softwareVersion, String hardwareVersion,
            List<List<String>> connections,
            String parentIdentifier
    ) {
        this.identifiers = identifiers;
        this.name = name;

        this.manufacturer = manufacturer;
        this.model = model;

        this.serialNumber = serialNumber;

        this.softwareVersion = softwareVersion;
        this.hardwareVersion = hardwareVersion;

        this.connections = connections;

        this.parentIdentifier = parentIdentifier;
    }

    public List<String> getIdentifiers() {
        return identifiers;
    }

    public String getName() {
        return name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getModel() {
        return model;
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

    public List<List<String>> getConnections() {
        return connections;
    }

    public String getParentIdentifier() {
        return parentIdentifier;
    }

    @Override
    public String toString() {
        return "MqttDiscoveryDevice{" +
                "identifiers='" + identifiers + '\'' +
                ", name='" + name + '\'' +
                ", parentIdentifier='" + parentIdentifier + '\'' +
                '}';
    }
}
