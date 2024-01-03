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

import java.net.Inet4Address;
import java.util.Objects;

import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.util.ToStringUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.xml.interfaces.CLUClassNameEnum;

public class CLUDevice {
    private final String name;

    private final Long serialNumber;

    private final String macAddress;

    private final byte[] iv;

    private Inet4Address address;

    private final CipherTypeEnum cipherType;

    private final byte[] privateKey;

    private CipherKey cipherKey;

    public CLUDevice(Inet4Address address, CipherTypeEnum cipherType) {
        this(
            null, null, address,
            cipherType
        );
    }

    public CLUDevice(Long serialNumber, String macAddress, Inet4Address address, CipherTypeEnum cipherType) {
        this(
            serialNumber, macAddress, address,
            cipherType,
            null, null
        );
    }

    public CLUDevice(Long serialNumber, String macAddress, Inet4Address address, CipherTypeEnum cipherType, byte[] iv, byte[] privateKey) {
        this(
            Util.mapNullSafe(serialNumber, value -> CLUClassNameEnum.CLU.name() + value),
            serialNumber, macAddress, address,
            cipherType,
            iv, privateKey
        );
    }

    public CLUDevice(String name, Long serialNumber, String macAddress, Inet4Address address, CipherTypeEnum cipherType, byte[] iv, byte[] privateKey) {
        this(
            name,
            serialNumber, macAddress, address,
            cipherType,
            iv, privateKey,
            (iv != null && privateKey != null) ? CipherKey.getInitialCipherKey(iv, privateKey) : null
        );
    }

    public CLUDevice(
        String name,
        Long serialNumber, String macAddress, Inet4Address address,
        CipherTypeEnum cipherType,
        byte[] iv, byte[] privateKey,
        CipherKey cipherKey
    ) {
        this.name = name;

        this.serialNumber = serialNumber;
        this.macAddress   = macAddress;
        this.address      = address;

        this.cipherType = cipherType;

        this.iv = iv;
        this.privateKey = privateKey;

        this.cipherKey = cipherKey;
    }

    public String getName() {
        return name;
    }

    public Long getSerialNumber() {
        return serialNumber;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public Inet4Address getAddress() {
        return address;
    }

    public void setAddress(Inet4Address address) {
        this.address = address;
    }

    public CipherTypeEnum getCipherType() {
        return cipherType;
    }

    public byte[] getIv() {
        return iv;
    }

    public byte[] getPrivateKey() {
        return privateKey;
    }

    public CipherKey getCipherKey() {
        return cipherKey;
    }

    public void setCipherKey(CipherKey cipherKey) {
        this.cipherKey = cipherKey;
    }

    @Override
    public String toString() {
        return "GrentonDevice{" +
               "name=" + name +
               ", serialNumber=" + ToStringUtil.toString(serialNumber) +
               ", macAddress=" + macAddress +
               ", address=" + ToStringUtil.toString(address) +
               ", cipherType=" + cipherType +
               ", iv=" + ToStringUtil.toString(iv) +
               ", privateKey=" + ToStringUtil.toString(privateKey) +
               ", cipherKey=" + cipherKey +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final CLUDevice that)) {
            return false;
        }

        return Objects.equals(getSerialNumber(), that.getSerialNumber());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSerialNumber());
    }
}
