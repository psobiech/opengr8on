package pl.psobiech.opengr8on.xml.omp.system.specificObjects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class Feature {
    @JacksonXmlProperty(isAttribute = true)
    private final Long id;

    private final String name;

    private final String hint;

    private final Boolean visible;

    private final FeatureType type;

    private final String accessType;

    private final String initValue;

    private final String defaultInitValue;

    private final String unit;

    private final Float divisor;

    private final Long index;

    private final String constrainAsString;

    @JsonCreator
    public Feature(Long id, String name, String hint, Boolean visible, FeatureType type, String accessType, String initValue, String defaultInitValue, String unit, Float divisor, Long index, String constrainAsString) {
        this.id = id;
        this.name = name;
        this.hint = hint;
        this.visible = visible;
        this.type = type;
        this.accessType = accessType;
        this.initValue = initValue;
        this.defaultInitValue = defaultInitValue;
        this.unit = unit;
        this.divisor = divisor;
        this.index = index;
        this.constrainAsString = constrainAsString;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getHint() {
        return hint;
    }

    public Boolean getVisible() {
        return visible;
    }

    public FeatureType getType() {
        return type;
    }

    public String getAccessType() {
        return accessType;
    }

    public String getInitValue() {
        return initValue;
    }

    public String getDefaultInitValue() {
        return defaultInitValue;
    }

    public String getUnit() {
        return unit;
    }

    public Float getDivisor() {
        return divisor;
    }

    public Long getIndex() {
        return index;
    }

    public String getConstrainAsString() {
        return constrainAsString;
    }
}
