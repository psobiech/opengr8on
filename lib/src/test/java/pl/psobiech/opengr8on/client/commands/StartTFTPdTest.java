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

import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.client.commands.StartTFTPdCommand.Request;
import pl.psobiech.opengr8on.client.commands.StartTFTPdCommand.Response;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StartTFTPdTest {
    @Test
    void correctRequest() {
        final Request input = StartTFTPdCommand.request();

        //

        final Request output = StartTFTPdCommand.requestFromByteArray(input.asByteArray())
                                                .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void correctResponse() {
        final Response input = StartTFTPdCommand.response();

        //

        final Response output = StartTFTPdCommand.responseFromByteArray(input.asByteArray())
                                                 .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void invalid() {
        byte[] buffer;

        //

        assertFalse(StartTFTPdCommand.requestFromByteArray(new byte[0]).isPresent());
        assertFalse(StartTFTPdCommand.requestFromByteArray(new byte[Request.COMMAND.length()]).isPresent());

        //

        assertFalse(StartTFTPdCommand.responseFromByteArray(new byte[0]).isPresent());
        assertFalse(StartTFTPdCommand.responseFromByteArray(new byte[Response.COMMAND.length()]).isPresent());

        buffer                  = new byte[100];
        buffer["resp".length()] = ':';
        assertFalse(StartTFTPdCommand.responseFromByteArray(buffer).isPresent());
    }
}
