package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class Event {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    private final String name;

    private final JsonNode argList;

    private final EventCommands commands;

    private final JsonNode customSchemeCommands;

    private final String hint;

    private final Long index;

    private final Boolean visible;

    public Event(Long id, String name, JsonNode argList, EventCommands commands, JsonNode customSchemeCommands, String hint, Long index, Boolean visible) {
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

    public EventCommands getCommands() {
        if (commands == null) {
            return new EventCommands(Collections.emptyList());
        }

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

    public static class EventCommands {
        @JsonMerge
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "string")
        private final List<String> commandValues;

        @JsonCreator
        public EventCommands(List<String> commandValues) {
            this.commandValues = commandValues;
        }

        public List<String> getCommandValues() {
            return Objects.requireNonNullElse(commandValues, Collections.emptyList());
        }
    }
}
