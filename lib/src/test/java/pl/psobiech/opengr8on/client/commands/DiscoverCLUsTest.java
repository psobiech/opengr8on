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

package pl.psobiech.opengr8on.client.commands;

import java.net.Inet4Address;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.CipherKey;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand.Request;
import pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand.Response;
import pl.psobiech.opengr8on.client.device.CLUDevice;
import pl.psobiech.opengr8on.client.device.CipherTypeEnum;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.util.RandomUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand.parse;
import static pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand.request;
import static pl.psobiech.opengr8on.client.commands.DiscoverCLUsCommand.response;

class DiscoverCLUsTest {
    @Test
    void hash() {
        final byte[] input = Base64.decodeBase64("dAp8ZAQI3fde2qg7AeXbe1ZJNZkaIzPcGGieBnTW");
        final byte[] expected = Base64.decodeBase64("ouqoyvvchxjk/wADCMGqutS84CNQM7aAxLoIPyTf");

        //

        final byte[] actual = DiscoverCLUsCommand.hash(input);

        //

        assertArrayEquals(expected, actual);
    }

    @Test
    void parseProjectCipherKey() {
        final CipherKey cipherKey = Mocks.cipherKey();

        final byte[] privateKey = "00000000".getBytes();
        final CipherKey cluCipherKey = CipherKey.getInitialCipherKey(cipherKey.getIV(), privateKey);

        final Inet4Address ipAddress = Mocks.ipAddress();

        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_SIZE);

        final long serialNumber = Mocks.serialNumber();
        final String macAddress = RandomUtil.hexString(12);

        final Response input = response(
            cluCipherKey.encrypt(DiscoverCLUsCommand.hash(randomBytes)), cipherKey.getIV(),
            serialNumber, macAddress
        );

        //

        final CLUDevice cluDevice = parse(
            randomBytes,
            Payload.of(ipAddress, 404, input.asByteArray()),
            Map.of(
                serialNumber, privateKey
            )
        )
            .get();

        //

        assertEquals(ipAddress, cluDevice.getAddress());
        assertArrayEquals(cipherKey.getIV(), cluDevice.getCipherKey().getIV());
        assertEquals(serialNumber, cluDevice.getSerialNumber());
        assertEquals(macAddress, cluDevice.getMacAddress());
        assertEquals(CipherTypeEnum.PROJECT, cluDevice.getCipherType());
        assertEquals(cluCipherKey, cluDevice.getCipherKey());
        assertArrayEquals(privateKey, cluDevice.getPrivateKey());
    }

    @Test
    void parseUnknownCipherKey() {
        final CipherKey cipherKey = Mocks.cipherKey();

        final byte[] privateKey = "00000000".getBytes();
        final CipherKey cluCipherKey = CipherKey.getInitialCipherKey(cipherKey.getIV(), privateKey);

        final Inet4Address ipAddress = Mocks.ipAddress();

        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_SIZE);

        final long serialNumber = Mocks.serialNumber();
        final String macAddress = RandomUtil.hexString(12);

        final Response input = response(
            cipherKey.encrypt(DiscoverCLUsCommand.hash(randomBytes)), cipherKey.getIV(),
            serialNumber, macAddress
        );

        //

        final CLUDevice cluDevice = parse(
            randomBytes,
            Payload.of(ipAddress, 404, input.asByteArray()),
            Map.of(
                serialNumber, privateKey
            )
        )
            .get();

        //

        assertEquals(ipAddress, cluDevice.getAddress());
        assertArrayEquals(cipherKey.getIV(), cluDevice.getCipherKey().getIV());
        assertEquals(serialNumber, cluDevice.getSerialNumber());
        assertEquals(macAddress, cluDevice.getMacAddress());
        assertEquals(CipherTypeEnum.NONE, cluDevice.getCipherType());
        assertEquals(cluCipherKey, cluDevice.getCipherKey());
        assertArrayEquals(privateKey, cluDevice.getPrivateKey());
    }

    @Test
    void parseUnknownPrivateKey() {
        final CipherKey cipherKey = Mocks.cipherKey();

        final Inet4Address ipAddress = Mocks.ipAddress();

        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_SIZE);

        final long serialNumber = Mocks.serialNumber();
        final String macAddress = RandomUtil.hexString(12);

        final Response input = response(
            cipherKey.encrypt(DiscoverCLUsCommand.hash(randomBytes)), cipherKey.getIV(),
            serialNumber, macAddress
        );

        //

        final CLUDevice cluDevice = parse(
            randomBytes,
            Payload.of(ipAddress, 404, input.asByteArray()),
            Map.of()
        )
            .get();

        //

        assertEquals(ipAddress, cluDevice.getAddress());
        assertEquals(serialNumber, cluDevice.getSerialNumber());
        assertEquals(macAddress, cluDevice.getMacAddress());
        assertEquals(CipherTypeEnum.UNKNOWN, cluDevice.getCipherType());
        assertNull(cluDevice.getCipherKey());
        assertNull(cluDevice.getPrivateKey());
    }

    @Test
    void parseInvalid() {
        final Inet4Address ipAddress = Mocks.ipAddress();

        final byte[] randomBytes = RandomUtil.bytes(Command.RANDOM_SIZE);

        final Payload payload = Payload.of(ipAddress, 404, new byte[0]);

        //

        final Optional<CLUDevice> optional = parse(randomBytes, payload, Map.of());

        //

        assertFalse(optional.isPresent());
    }

    @Test
    void correctRequest() {
        final Request input = request(
            RandomUtil.bytes(32), RandomUtil.bytes(16), Mocks.ipAddress()
        );

        //

        final Request output = DiscoverCLUsCommand.requestFromByteArray(input.asByteArray())
                                                  .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void correctResponse() {
        final Response input = response(
            RandomUtil.bytes(32), RandomUtil.bytes(16),
            HexUtil.asLong(RandomUtil.hexString(8)), RandomUtil.hexString(12)
        );

        //

        final Response output = DiscoverCLUsCommand.responseFromByteArray(input.asByteArray())
                                                   .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void invalid() {
        byte[] buffer;

        //

        assertFalse(DiscoverCLUsCommand.requestFromByteArray(new byte[0]).isPresent());
        assertFalse(DiscoverCLUsCommand.requestFromByteArray(new byte[100]).isPresent());

        buffer                                                                                                                          = new byte[100];
        buffer[Command.RANDOM_ENCRYPTED_SIZE]                                                                                           = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE]                                                                     = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Request.COMMAND.length()]                                      = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Request.COMMAND.length() + 1 + Command.MIN_SERIAL_NUMBER_SIZE] = ':';
        assertFalse(DiscoverCLUsCommand.requestFromByteArray(buffer).isPresent());

        //

        assertFalse(DiscoverCLUsCommand.responseFromByteArray(new byte[0]).isPresent());
        assertFalse(DiscoverCLUsCommand.responseFromByteArray(new byte[100]).isPresent());

        buffer                                                                                                                           = new byte[100];
        buffer[Command.RANDOM_ENCRYPTED_SIZE]                                                                                            = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE]                                                                      = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Response.COMMAND.length()]                                      = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Response.COMMAND.length() + 1 + Command.MIN_SERIAL_NUMBER_SIZE] = ':';
        assertFalse(DiscoverCLUsCommand.responseFromByteArray(buffer).isPresent());
    }
}
