/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.xml.interfaces;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
public class CLUInterfaceFeature {
    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true)
    private String type;

    @JacksonXmlProperty(isAttribute = true)
    private String unit;

    @JacksonXmlProperty(isAttribute = true, localName = "default")
    private String _default;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean get;

    @JacksonXmlProperty(isAttribute = true)
    private Boolean set;

    @JacksonXmlProperty(isAttribute = true)
    private int index;

    @JsonIgnore
    private List<String> enumValues;

    @JsonIgnore
    private List<CLUInterfaceFeatureEnum> enums = new ArrayList<>();

    private Desc desc;

    public String getName() {
        return name;
    }

    public CLUDataTypeEnum getType() {
        return CLUDataTypeEnum.of(type);
    }

    public String getUnit() {
        return unit;
    }

    public String getDefault() {
        return _default;
    }

    public Boolean getGet() {
        return get;
    }

    public Boolean getSet() {
        return set;
    }

    public int getIndex() {
        return index;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public List<CLUInterfaceFeatureEnum> getEnums() {
        return enums;
    }

    public String getResKey() {
        return desc.getResKey();
    }

    @JsonAnySetter
    public void setServices(String name, Object value) {
        if (name.equals("enum")) {
            if (value instanceof String) {
                enumValues = List.of(((String) value).split(",+"));
            } else if (value instanceof Map) {
                enums.add(ObjectMapperFactory.XML.convertValue(value, CLUInterfaceFeatureEnum.class));
            }
        }
    }
}
