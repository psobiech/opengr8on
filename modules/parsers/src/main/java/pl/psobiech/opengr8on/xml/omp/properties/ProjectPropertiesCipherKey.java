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

package pl.psobiech.opengr8on.xml.omp.properties;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.psobiech.opengr8on.xml.interfaces.Value;

public class ProjectPropertiesCipherKey {
    @JsonProperty("keyBytes")
    private Value keyBytes;

    @JsonProperty("ivBytes")
    private Value ivBytes;

    public Value getKeyBytes() {
        return keyBytes;
    }

    public Value getIvBytes() {
        return ivBytes;
    }
}
