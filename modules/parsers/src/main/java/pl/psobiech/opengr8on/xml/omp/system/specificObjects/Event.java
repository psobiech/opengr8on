package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Event {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    private final String name;

    private final JsonNode argList;

    private final JsonNode commands;

    private final JsonNode customSchemeCommands;

    private final String hint;

    private final Long index;

    private final Boolean visible;

    public Event(Long id, String name, JsonNode argList, JsonNode commands, JsonNode customSchemeCommands, String hint, Long index, Boolean visible) {
        this.id = id;
        this.name = name;
        this.argList = argList;
        this.commands = commands;
        this.customSchemeCommands = customSchemeCommands;
        this.hint = hint;
        this.index = index;
        this.visible = visible;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public JsonNode getArgList() {
        return argList;
    }

    public JsonNode getCommands() {
        return commands;
    }

    public JsonNode getCustomSchemeCommands() {
        return customSchemeCommands;
    }

    public String getHint() {
        return hint;
    }

    public Long getIndex() {
        return index;
    }

    public Boolean getVisible() {
        return visible;
    }
}
