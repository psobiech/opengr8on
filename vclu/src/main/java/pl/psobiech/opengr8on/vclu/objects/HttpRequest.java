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

package pl.psobiech.opengr8on.vclu.objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.vclu.VirtualObject;

public class HttpRequest extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

    public HttpRequest(String name) {
        super(name);

    }

    private enum Features implements IFeature {
        HOST(0),
        PATH(1),
        QUERY(2),
        METHOD(3),
        TIMEOUT(4),
        REQUEST_TYPE(5),
        RESPONSE_TYPE(6),
        REQUEST_HEADERS(7),
        REQUEST_BODY(8),
        RESPONSE_HEADERS(9),
        RESPONSE_BODY(10),
        STATUS_CODE(11),
        ACTIVE(12),
        //
        ;

        private final int index;

        Features(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }

    private enum Methods implements IMethod {
        SEND_REQUEST(0),
        ABORT_REQUEST(1),
        CLEAR(2),
        //
        ;

        private final int index;

        Methods(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return index;
        }
    }

    private enum Events implements IEvent {
        REQUEST_SENT(0),
        RESPONSE(1)
        //
        ;

        private final int address;

        Events(int address) {
            this.address = address;
        }

        @Override
        public int address() {
            return address;
        }
    }

    private enum HttpType {
        NONE(0),
        TEXT(1),
        JSON(2),
        XML(3),
        FORM_DATA(4),
        OTHER(5),
        //
        ;

        private final int value;

        HttpType(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }
    }
}
