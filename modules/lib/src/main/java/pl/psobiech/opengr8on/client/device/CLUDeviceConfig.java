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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CLUDeviceConfig extends DeviceConfig {
    @JsonProperty("mac")
    private final String macAddress;

    @JsonProperty("tfbusDevices")
    private final List<DeviceConfig> tfBusDevices;

    @JsonCreator
    public CLUDeviceConfig(
        String serialNumber,
        String macAddress,
        int hardwareType, long hardwareVersion,
        int firmwareType, int firmwareVersion,
        String fwVer,
        String status,
        List<DeviceConfig> tfBusDevices
    ) {
        super(
            serialNumber,
            hardwareType, hardwareVersion,
            firmwareType, firmwareVersion, fwVer,
            status
        );

        this.macAddress = macAddress;
        this.tfBusDevices = tfBusDevices;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public List<DeviceConfig> getTFBusDevices() {
        return tfBusDevices;
    }

    @Override
    public String toString() {
        return "CLUDeviceConfig{" +
               "macAddress=" + macAddress +
               ", tfBusDevices=" + tfBusDevices +
               "} " + super.toString();
    }
}
