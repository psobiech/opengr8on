package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class FeatureType {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    private final String paramType;

    private final JsonNode restrictionValues;

    private final JsonNode restrictionValuesName;

    private final String restrictionType;

    @JsonCreator
    public FeatureType(Long id, String paramType, JsonNode restrictionValues, JsonNode restrictionValuesName, String restrictionType) {
        this.id = id;
        this.paramType = paramType;
        this.restrictionValues = restrictionValues;
        this.restrictionValuesName = restrictionValuesName;
        this.restrictionType = restrictionType;
    }

    public Long getId() {
        return id;
    }

    public String getParamType() {
        return paramType;
    }

    public JsonNode getRestrictionValues() {
        return restrictionValues;
    }

    public JsonNode getRestrictionValuesName() {
        return restrictionValuesName;
    }

    public String getRestrictionType() {
        return restrictionType;
    }
}
