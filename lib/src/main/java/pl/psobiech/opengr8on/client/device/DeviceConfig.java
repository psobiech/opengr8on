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

package pl.psobiech.opengr8on.client.device;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.util.Util;

public class DeviceConfig {
    @JsonProperty("sn")
    protected final Long serialNumber;

    @JsonProperty("hwType")
    protected final int hardwareType;

    @JsonProperty("hwVer")
    protected final long hardwareVersion;

    @JsonProperty("fwType")
    protected final int firmwareType;

    @JsonProperty("fwApiVer")
    protected final int firmwareVersion;

    @JsonProperty("status")
    protected final String status;

    @JsonCreator
    public DeviceConfig(
        String serialNumber,
        int hardwareType, long hardwareVersion,
        int firmwareType, int firmwareVersion,
        String status
    ) {
        this.serialNumber    = Util.mapNullSafe(serialNumber, Long::parseLong);
        this.hardwareType    = hardwareType;
        this.hardwareVersion = hardwareVersion;
        this.firmwareType    = firmwareType;
        this.firmwareVersion = firmwareVersion;
        this.status          = status;
    }

    public Long getSerialNumber() {
        return serialNumber;
    }

    public int getHardwareType() {
        return hardwareType;
    }

    public long getHardwareVersion() {
        return hardwareVersion;
    }

    public int getFirmwareType() {
        return firmwareType;
    }

    public int getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "DeviceConfig{" +
               "serialNumber=" + ToStringUtil.toString(serialNumber) +
               ", hardwareType=" + ToStringUtil.toString(hardwareType) +
               ", hardwareVersion=" + ToStringUtil.toString(hardwareVersion) +
               ", firmwareType=" + ToStringUtil.toString(firmwareType) +
               ", firmwareVersion=" + ToStringUtil.toString(firmwareVersion) +
               ", status=" + status +
               '}';
    }
}
