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

import java.util.List;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

public class CLUInterfaceMethod {
    @JacksonXmlProperty(isAttribute = true)
    private int index;

    @JacksonXmlProperty(isAttribute = true)
    private String call;

    @JacksonXmlProperty(isAttribute = true)
    private String name;

    @JacksonXmlProperty(isAttribute = true, localName = "return")
    private String _return;

    @JacksonXmlProperty(isAttribute = true)
    private String unit;

    @JacksonXmlProperty(localName = "param")
    private List<CLUInterfaceMethodParam> params;

    private Desc desc;

    public int getIndex() {
        return index;
    }

    public String getCall() {
        return call;
    }

    public String getName() {
        return name;
    }

    public CLUDataTypeEnum getReturn() {
        return CLUDataTypeEnum.of(_return);
    }

    public String getUnit() {
        return unit;
    }

    public List<CLUInterfaceMethodParam> getParams() {
        return params;
    }

    public Desc getDesc() {
        return desc;
    }
}
