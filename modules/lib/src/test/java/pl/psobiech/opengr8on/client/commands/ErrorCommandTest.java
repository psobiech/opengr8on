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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import pl.psobiech.opengr8on.client.commands.ErrorCommand.Response;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Execution(ExecutionMode.CONCURRENT)
class ErrorCommandTest {
    @Test
    void correctResponse() {
        final Response input = ErrorCommand.response();

        //

        final Response output = ErrorCommand.responseFromByteArray(input.asByteArray())
                                            .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void invalid() {
        byte[] buffer;

        //

        assertFalse(ErrorCommand.responseFromByteArray(new byte[0]).isPresent());
        assertFalse(ErrorCommand.responseFromByteArray(new byte[Response.COMMAND.length()]).isPresent());

        buffer                  = new byte[100];
        buffer["resp".length()] = ':';
        assertFalse(ErrorCommand.responseFromByteArray(buffer).isPresent());
    }
}
