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
import pl.psobiech.opengr8on.client.Command;
import pl.psobiech.opengr8on.client.Mocks;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand.Request;
import pl.psobiech.opengr8on.client.commands.LuaScriptCommand.Response;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.SocketUtil.Payload;

import java.net.Inet4Address;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Execution(ExecutionMode.CONCURRENT)
class LuaScriptCommandTest {
    @Test
    void parsePayload() {
        final Inet4Address ipAddress = Mocks.ipAddress();
        final Integer sessionId = Mocks.sessionId();
        final String expectedReturnValue = "nil";

        final Response input = LuaScriptCommand.response(
                ipAddress, sessionId, expectedReturnValue
        );

        //

        final String returnValue = LuaScriptCommand.parse(
                                                           sessionId,
                                                           Payload.of(ipAddress, 404, input.asByteArray())
                                                   )
                                                   .get();

        //

        assertEquals(expectedReturnValue, returnValue);
    }

    @Test
    void correctRequest() {
        final Request input = LuaScriptCommand.request(
                Mocks.ipAddress(), Mocks.sessionId(), LuaScriptCommand.CHECK_ALIVE
        );

        //

        final Request output = LuaScriptCommand.requestFromByteArray(input.asByteArray())
                                               .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
        assertArrayEquals(
                FileUtil.CRLF.getBytes(),
                Arrays.copyOfRange(output.asByteArray(), output.asByteArray().length - 2, output.asByteArray().length)
        );
    }

    @Test
    void correctResponse() {
        final Response input = LuaScriptCommand.response(
                Mocks.ipAddress(), Mocks.sessionId(), "nil"
        );

        //

        final Response output = LuaScriptCommand.responseFromByteArray(input.asByteArray())
                                                .get();

        //

        assertArrayEquals(input.asByteArray(), output.asByteArray());
    }

    @Test
    void invalid() {
        byte[] buffer;

        //

        assertFalse(LuaScriptCommand.requestFromByteArray(new byte[0]).isPresent());
        assertFalse(LuaScriptCommand.requestFromByteArray(new byte[100]).isPresent());

        buffer = new byte[100];
        buffer[Request.COMMAND.length()] = ':';
        buffer[Request.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS] = ':';
        buffer[Request.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS + 1 + Command.MIN_SESSION_CHARACTERS] = ':';
        assertFalse(LuaScriptCommand.requestFromByteArray(buffer).isPresent());

        //

        assertFalse(LuaScriptCommand.responseFromByteArray(new byte[0]).isPresent());
        assertFalse(LuaScriptCommand.responseFromByteArray(new byte[100]).isPresent());

        buffer = new byte[100];
        buffer[Response.COMMAND.length()] = ':';
        buffer[Response.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS] = ':';
        buffer[Response.COMMAND.length() + 1 + Command.MIN_IP_CHARACTERS + 1 + Command.MIN_SESSION_CHARACTERS] = ':';
        assertFalse(LuaScriptCommand.responseFromByteArray(buffer).isPresent());
    }
}
