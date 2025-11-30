package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Module {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    @JacksonXmlProperty(isAttribute = true)
    private final Long reference;

    private final String moduleId;

    private final String innerName;

    private final String moduleClass;

    private final String moduleTypeFirmware;

    private final String moduleVersion;

    private final Boolean active;

    private final JsonNode moduleInterface;

    public Module(Long id, Long reference, String moduleId, String innerName, String moduleClass, String moduleTypeFirmware, String moduleVersion, Boolean active, JsonNode moduleInterface) {
        this.id = id;
        this.reference = reference;
        this.moduleId = moduleId;
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

    public String getModuleId() {
        return moduleId;
    }

    public String getInnerName() {
        return innerName;
    }

    public Long getModuleClass() {
        return Long.parseLong(moduleClass, 16);
    }

    public Integer getModuleTypeFirmware() {
        return Integer.parseInt(moduleTypeFirmware, 10);
    }

    public Integer getModuleVersion() {
        return Integer.parseInt(moduleVersion, 10);
    }

    public Boolean getActive() {
        return active;
    }

    public JsonNode getModuleInterface() {
        return moduleInterface;
    }

    @Override
    public String toString() {
        return "Module{" +
                "id=" + id +
                ", reference=" + reference +
                ", moduleId='" + moduleId + '\'' +
                ", innerName='" + innerName + '\'' +
                ", moduleClass='" + moduleClass + '\'' +
                ", moduleTypeFirmware='" + moduleTypeFirmware + '\'' +
                ", moduleVersion='" + moduleVersion + '\'' +
                ", active=" + active +
                ", moduleInterface=" + moduleInterface +
                '}';
    }
}
