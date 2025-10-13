package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class IODefinition {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    @JacksonXmlProperty(isAttribute = true)
    private final Long reference;

    private final String moduleID;

    private final String innerName;

    private final String moduleClass;

    private final String moduleTypeFirmware;

    private final String moduleVersion;

    private final Boolean active;

    private final JsonNode moduleInterface;

    public IODefinition(Long id, Long reference, String moduleID, String innerName, String moduleClass, String moduleTypeFirmware, String moduleVersion, Boolean active, JsonNode moduleInterface) {
        this.id = id;
        this.reference = reference;
        this.moduleID = moduleID;
        this.innerName = innerName;
        this.moduleClass = moduleClass;
        this.moduleTypeFirmware = moduleTypeFirmware;
        this.moduleVersion = moduleVersion;
        this.active = active;
        this.moduleInterface = moduleInterface;
    }

    public Long getId() {
        return id;
    }

    public Long getReference() {
        return reference;
    }

    public String getModuleID() {
        return moduleID;
    }

    public String getInnerName() {
        return innerName;
    }

    public String getModuleClass() {
        return moduleClass;
    }

    public String getModuleTypeFirmware() {
        return moduleTypeFirmware;
    }

    public String getModuleVersion() {
        return moduleVersion;
    }

    public Boolean getActive() {
        return active;
    }

    public JsonNode getModuleInterface() {
        return moduleInterface;
    }
}
