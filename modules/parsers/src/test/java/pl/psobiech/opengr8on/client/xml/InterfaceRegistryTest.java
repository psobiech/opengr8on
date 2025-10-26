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

package pl.psobiech.opengr8on.client.xml;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.psobiech.opengr8on.util.HexUtil;
import pl.psobiech.opengr8on.xml.interfaces.*;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class InterfaceRegistryTest {
    private static InterfaceRegistry interfaceRegistry;

    @BeforeAll
    static void beforeAll() {
        interfaceRegistry = new InterfaceRegistry(Paths.get("../../runtime/device-interfaces"));
    }

    @Test
    void loadUnknownClu() {
        final Optional<CLU> unknownCLU = interfaceRegistry.getCLU(0, 0, 0, 0);

        assertFalse(unknownCLU.isPresent());
    }

    @Test
    void loadClu() {
        final Optional<CLU> vcluOptional = interfaceRegistry.getCLU(0x13, 0x1, 0x3, 0xAA55AA);

        assertTrue(vcluOptional.isPresent());

        final CLU vclu = vcluOptional.get();
        assertEquals(0x13, HexUtil.asInt(vclu.getHardwareType()));
        assertEquals(0x1, HexUtil.asInt(vclu.getHardwareVersion()));
        assertEquals(0x3, HexUtil.asInt(vclu.getFirmwareType()));
        assertEquals(0xAA55AA, HexUtil.asInt(vclu.getFirmwareVersion()));

        assertEquals(CLUClassNameEnum.CLU, vclu.getClassName());
        assertEquals("CLU_VIRTUAL_OPENGR8ON", vclu.getTypeName());

        // TODO: more granular checks
        final CLUInterface anInterface = vclu.getInterface();
        assertEquals(19, anInterface.getFeatures().size());
        assertEquals(5, anInterface.getMethods().size());
        assertEquals(3, anInterface.getEvents().size());

        final List<CLUObjectRestriction> objects = vclu.getObjects();
        assertEquals(4, objects.size());
    }

    @Test
    void loadMqttTopic() {
        final Optional<CLUObject> mqttTopicOptional = interfaceRegistry.getObject("MqttTopic", 1);
        assertTrue(mqttTopicOptional.isPresent());

        final CLUObject mqttTopic = mqttTopicOptional.get();
        assertEquals("MqttTopic", mqttTopic.getName());
        assertEquals(999, Integer.parseInt(mqttTopic.getClazz()));
        assertEquals(1, HexUtil.asInt(mqttTopic.getVersion()));

        assertEquals("OBJECT", mqttTopic.getClassName());

        // TODO: more granular checks
        assertEquals(2, mqttTopic.getFeatures().size());
        assertEquals(5, mqttTopic.getMethods().size());
        assertEquals(2, mqttTopic.getEvents().size());
    }
}
