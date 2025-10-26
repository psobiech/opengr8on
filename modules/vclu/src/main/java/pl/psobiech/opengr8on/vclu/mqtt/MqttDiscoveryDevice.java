package pl.psobiech.opengr8on.vclu.mqtt;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.psobiech.opengr8on.xml.omp.system.specificObjects.SpecificObject;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqttDiscoveryDevice {
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

    public MqttDiscoveryDevice(SpecificObject clu) {
        this(
                clu.getNameOnCLU(), clu.getName(), "Grenton",
                clu.getSerialNumber(),
                clu.getFirmwareType() + "_" + clu.getFirmwareVersion(),
                clu.getHardwareType() + "_" + clu.getHardwareVersion()
        );
    }

    @JsonCreator
    public MqttDiscoveryDevice(String identifier, String name, String manufacturer, String serialNumber, String softwareVersion, String hardwareVersion) {
        this.identifier = identifier;
        this.name = name;
        this.manufacturer = manufacturer;
        this.serialNumber = serialNumber;
        this.softwareVersion = softwareVersion;
        this.hardwareVersion = hardwareVersion;
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
}
