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

package pl.psobiech.opengr8on.vclu.system.objects.clu;

import java.time.ZoneId;
import java.time.ZoneOffset;

public enum CLUTimeZone {
    EUROPE_WARSAW(0, ZoneId.of("Europe/Warsaw")),
    EUROPE_LONDON(1, ZoneId.of("Europe/London")),
    EUROPE_MOSCOW(2, ZoneId.of("Europe/Moscow")),
    EUROPE_ISTANBUL(3, ZoneId.of("Europe/Istanbul")),
    EUROPE_ATHENS(4, ZoneId.of("Europe/Athens")),
    ASIA_DUBAI(5, ZoneId.of("Asia/Dubai")),
    ASIA_JAKARTA(6, ZoneId.of("Asia/Jakarta")),
    ASIA_HONG_KONG(7, ZoneId.of("Asia/Hong_Kong")),
    AUSTRALIA_SYDNEY(8, ZoneId.of("Australia/Sydney")),
    AUSTRALIA_PERTH(9, ZoneId.of("Australia/Perth")),
    AUSTRALIA_BRISBANE(10, ZoneId.of("Australia/Brisbane")),
    PACIFIC_AUCKLAND(11, ZoneId.of("Pacific/Auckland")),
    PACIFIC_HONOLULU(12, ZoneId.of("Pacific/Honolulu")),
    AMERICA_ANCHORAGE(13, ZoneId.of("America/Anchorage")),
    AMERICA_CHICAGO(14, ZoneId.of("America/Chicago")),
    AMERICA_NEW_YORK(15, ZoneId.of("America/New_York")),
    AMERICA_BARBADOS(16, ZoneId.of("America/Barbados")),
    AMERICA_SAO_PAULO(17, ZoneId.of("America/Sao_Paulo")),
    AMERICA_BOGOTA(18, ZoneId.of("America/Bogota")),
    AMERICA_BUENOS_AIRES(19, ZoneId.of("America/Buenos_Aires")),
    AMERICA_CHICAGO_2(20, ZoneId.of("America/Chicago")),
    AMERICA_LOS_ANGELES(21, ZoneId.of("America/Los_Angeles")),
    UTC(22, ZoneOffset.UTC),
    //
    ;

    private final int value;

    private final ZoneId zoneId;

    CLUTimeZone(int value, ZoneId zoneId) {
        this.value = value;
        this.zoneId = zoneId;
    }

    public static CLUTimeZone valueOf(int value) {
        final CLUTimeZone[] values = CLUTimeZone.values();
        if (value >= values.length) {
            return CLUTimeZone.UTC;
        }

        return values[value];
    }

    public int value() {
        return value;
    }

    public ZoneId zoneId() {
        return zoneId;
    }
}
