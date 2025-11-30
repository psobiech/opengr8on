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

package pl.psobiech.opengr8on.xml.interfaces;

import java.util.Collections;
import java.util.List;

public class CLUInterface {
    public static final CLUInterface EMPTY = new CLUInterface(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

    private final List<CLUInterfaceFeature> features;

    private final List<CLUInterfaceMethod> methods;

    private final List<CLUInterfaceEvent> events;

    public CLUInterface(List<CLUInterfaceFeature> features, List<CLUInterfaceMethod> methods, List<CLUInterfaceEvent> events) {
        this.features = features;
        this.methods = methods;
        this.events = events;
    }

    public List<CLUInterfaceFeature> getFeatures() {
        return features;
    }

    public List<CLUInterfaceMethod> getMethods() {
        return methods;
    }

    public List<CLUInterfaceEvent> getEvents() {
        return events;
    }
}
