package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import pl.psobiech.opengr8on.util.Util;

import java.util.List;
import java.util.Optional;

public class SpecificObject {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    @JacksonXmlProperty(isAttribute = true)
    private final Long reference;

    private final String name;
    private final String nameOnCLU;

    @JsonProperty("class")
    @JacksonXmlProperty(isAttribute = true)
    private final String objectClass;

    private final String type;
    private final String description;

    private final String ipAddress;
    private final List<JsonNode> labels;
    private final List<Feature> features;
    private final List<Event> events;
    private final Clu clu;
    private final List<JsonNode> module;
    private final String classTypeId;
    private final String sourceReceiverTypeID;
    private final String number;
    private final Boolean active;
    private final Boolean statisticState;
    private final Long tfBusOrder;
    //
    private final List<JsonNode> definedFeaturesList;
    @JsonProperty("embeddedFeaturesList")
    private final List<Feature> embeddedFeaturesList;
    private final List<Event> eventsList;
    private final List<IO> iosList;
    private final List<JsonNode> scripts;
    private final List<JsonNode> peripheryList;
    private final List<JsonNode> applicationsList;
    private final List<IODefinition> modulesList;
    private final List<JsonNode> methodsList;

    @JsonProperty("isRemoved")
    private final Boolean removed;
    private final Boolean visible;
    private final Boolean validConfigurationOnCLU;

    private final String firmwareVersion;
    private final String firmwareType;
    private final String hardwareVersion;
    private final String hardwareType;
    private final String serialNumber;
    private final String macAddress;
    private final String cipherKeyType;
    private final String iv;
    private final String privateKey;
    private final Boolean encrypted;

    public SpecificObject(Long id, Long reference, String name, String nameOnCLU, String type, String description, String ipAddress, List<JsonNode> labels, List<Feature> features, List<Event> events, Clu clu, List<JsonNode> module, String classTypeId, String sourceReceiverTypeID, String number, Boolean active, Boolean statisticState, Long tfBusOrder, List<JsonNode> definedFeaturesList, List<Feature> embeddedFeaturesList, List<Event> eventsList, List<IO> iosList, List<JsonNode> scripts, List<JsonNode> peripheryList, List<JsonNode> applicationsList, List<IODefinition> modulesList, List<JsonNode> methodsList, Boolean visible, Boolean validConfigurationOnCLU, String firmwareVersion, String firmwareType, String hardwareVersion, String hardwareType, String serialNumber, String macAddress, String cipherKeyType, String iv, String privateKey, Boolean encrypted, Boolean removed, String objectClass) {
        this.id = id;
        this.reference = reference;
        this.name = name;
        this.nameOnCLU = nameOnCLU;
        this.type = type;
        this.description = description;
        this.ipAddress = ipAddress;
        this.labels = labels;
        this.features = features;
        this.events = events;
        this.clu = clu;
        this.module = module;
        this.classTypeId = classTypeId;
        this.sourceReceiverTypeID = sourceReceiverTypeID;
        this.number = number;
        this.active = active;
        this.statisticState = statisticState;
        this.tfBusOrder = tfBusOrder;
        this.definedFeaturesList = definedFeaturesList;
        this.embeddedFeaturesList = embeddedFeaturesList;
        this.eventsList = eventsList;
        this.iosList = iosList;
        this.scripts = scripts;
        this.peripheryList = peripheryList;
        this.applicationsList = applicationsList;
        this.modulesList = modulesList;
        this.methodsList = methodsList;
        this.visible = visible;
        this.validConfigurationOnCLU = validConfigurationOnCLU;
        this.firmwareVersion = firmwareVersion;
        this.firmwareType = firmwareType;
        this.hardwareVersion = hardwareVersion;
        this.hardwareType = hardwareType;
        this.serialNumber = serialNumber;
        this.macAddress = macAddress;
        this.cipherKeyType = cipherKeyType;
        this.iv = iv;
        this.privateKey = privateKey;
        this.encrypted = encrypted;
        this.removed = removed;
        this.objectClass = objectClass;
    }

    public SpecificObjectClass getObjectClass() {
        final String objectClassAsString = getObjectClassAsString();
        if (objectClassAsString == null) {
            if (this instanceof Clu) {
                return SpecificObjectClass.CLU;
            }
        }

        return SpecificObjectClass.fromRawValue(objectClassAsString);
    }

    public String getObjectClassAsString() {
        return objectClass;
    }

    public SpecificObjectType getType() {
        final String typeAsString = getTypeAsString();
        if (typeAsString == null) {
            final SpecificObjectClass objectClass = getObjectClass();
            if (objectClass == SpecificObjectClass.CLU) {
                return SpecificObjectType.CLU;
            }

            if (objectClass == SpecificObjectClass.SCRIPT) {
                return SpecificObjectType.SCRIPT;
            }

            if (objectClass == SpecificObjectClass.CONTAINER) {
                return SpecificObjectType.CONTAINER;
            }

            if (this instanceof Clu) {
                return SpecificObjectType.CLU;
            }
        }

        return SpecificObjectType.fromRawValue(typeAsString);
    }

    public String getTypeAsString() {
        return type;
    }

    //

    public Optional<Long> findId() {
        return Optional.ofNullable(getId());
    }

    public Long getId() {
        return id;
    }

    public Long getReference() {
        return reference;
    }

    public String getName() {
        return name;
    }

    public String getNameOnCLU() {
        return nameOnCLU;
    }

    public String getDescription() {
        return description;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public List<JsonNode> getLabels() {
        return Util.nullAsEmpty(labels);
    }

    public List<Feature> getFeatures() {
        return Util.nullAsEmpty(features);
    }

    public List<Event> getEvents() {
        return Util.nullAsEmpty(events);
    }

    public Clu getClu() {
        return clu;
    }

    public List<JsonNode> getModule() {
        return module;
    }

    public String getClassTypeId() {
        return classTypeId;
    }

    public String getSourceReceiverTypeID() {
        return sourceReceiverTypeID;
    }

    public String getNumber() {
        return number;
    }

    public Boolean getActive() {
        return active;
    }

    public Boolean getStatisticState() {
        return statisticState;
    }

    public Long getTfBusOrder() {
        return tfBusOrder;
    }

    public List<JsonNode> getDefinedFeaturesList() {
        return Util.nullAsEmpty(definedFeaturesList);
    }

    public List<Feature> getEmbeddedFeaturesList() {
        return Util.nullAsEmpty(embeddedFeaturesList);
    }

    public List<Event> getEventsList() {
        return Util.nullAsEmpty(eventsList);
    }

    public List<IO> getIosList() {
        return Util.nullAsEmpty(iosList);
    }

    public List<JsonNode> getScripts() {
        return Util.nullAsEmpty(scripts);
    }

    public List<JsonNode> getPeripheryList() {
        return Util.nullAsEmpty(peripheryList);
    }

    public List<JsonNode> getApplicationsList() {
        return Util.nullAsEmpty(applicationsList);
    }

    public List<IODefinition> getModulesList() {
        return Util.nullAsEmpty(modulesList);
    }

    public List<JsonNode> getMethodsList() {
        return Util.nullAsEmpty(methodsList);
    }

    public Boolean getRemoved() {
        return removed;
    }

    public Boolean getVisible() {
        return visible;
    }

    public Boolean getValidConfigurationOnCLU() {
        return validConfigurationOnCLU;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getFirmwareType() {
        return firmwareType;
    }

    public String getHardwareVersion() {
        return hardwareVersion;
    }

    public String getHardwareType() {
        return hardwareType;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getCipherKeyType() {
        return cipherKeyType;
    }

    public String getIv() {
        return iv;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public Boolean getEncrypted() {
        return encrypted;
    }

    @Override
    public String toString() {
        return "SpecificObject{" +
                "id=" + getId() +
                ", reference=" + getReference() +
                ", name='" + getName() + '\'' +
                ", nameOnCLU='" + getNameOnCLU() + '\'' +
                ", clu='" + getClu() + '\'' +
                ", objectClass='" + getObjectClassAsString() + "'/" + getObjectClass() +
                ", type='" + getTypeAsString() + "'/" + getType() +
                '}';
    }

}
