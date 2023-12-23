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

package pl.psobiech.opengr8on.client.commands;

import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.commands.SetKeyCommand.Request;
import pl.psobiech.opengr8on.client.commands.SetKeyCommand.Response;
import pl.psobiech.opengr8on.util.RandomUtil;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SetKeyCommandTest {
    @Test
    void correctRequest() {
        final Request input = SetKeyCommand.request(
            RandomUtil.bytes(32), RandomUtil.bytes(16), RandomUtil.bytes(16)
        );

        //

        final Request output = SetKeyCommand.requestFromByteArray(input.asByteArray())
                                            .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void correctResponse() {
        final Response input = SetKeyCommand.response();

        //

        final Response output = SetKeyCommand.responseFromByteArray(input.asByteArray())
                                             .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void invalid() {
        byte[] buffer;

        //

        assertFalse(SetKeyCommand.requestFromByteArray(new byte[0]).isPresent());
        assertFalse(SetKeyCommand.requestFromByteArray(new byte[100]).isPresent());

        buffer = new byte[100];
        buffer[Command.RANDOM_ENCRYPTED_SIZE] = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE] = ':';
        buffer[Command.RANDOM_ENCRYPTED_SIZE + 1 + Command.IV_SIZE + 1 + Request.COMMAND.length()] = ':';
        assertFalse(SetKeyCommand.requestFromByteArray(buffer).isPresent());

        //

        assertFalse(SetKeyCommand.responseFromByteArray(new byte[0]).isPresent());
        assertFalse(SetKeyCommand.responseFromByteArray(new byte[100]).isPresent());

        buffer = new byte[100];
        buffer[Response.COMMAND.length()] = ':';
        assertFalse(SetKeyCommand.responseFromByteArray(buffer).isPresent());
    }
}
