/*
 * OpenGr8on, open source extensions to systems based on Grenton devices
 * Copyright (C) 2023 Piotr Sobiech
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package pl.psobiech.opengr8on.xml.interfaces;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@XmlAccessorType(XmlAccessType.FIELD)
public class CLU {
    @JacksonXmlProperty(isAttribute = true)
    private CLUClassNameEnum className;

    @JacksonXmlProperty(isAttribute = true)
    private String firmwareType;

    @JacksonXmlProperty(isAttribute = true)
    private String firmwareVersion;

    @JacksonXmlProperty(isAttribute = true)
    private String hardwareType;

    @JacksonXmlProperty(isAttribute = true)
    private String hardwareVersion;

    @JacksonXmlProperty(isAttribute = true)
    private String typeName;

    @JsonProperty("interface")
    private CLUInterface _interface;

    private List<CLUObjectRestriction> objects;

    private List<CLUModuleVersionConstraint> modulesVersionConstraints;

    private List<CLUOption> options;

    public CLUClassNameEnum getClassName() {
        if (className == null) {
            return CLUClassNameEnum.CLU;
        }

        return className;
    }

    public String getFirmwareType() {
        return firmwareType;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getHardwareType() {
        return hardwareType;
    }

    public String getHardwareVersion() {
        return hardwareVersion;
    }

    public String getTypeName() {
        return typeName;
    }

    public CLUInterface getInterface() {
        return _interface;
    }

    public List<CLUObjectRestriction> getObjects() {
        return objects;
    }

    public List<CLUModuleVersionConstraint> getModuleVersionConstraints() {
        return modulesVersionConstraints;
    }

    public List<CLUOption> getOptions() {
        return options;
    }
}
