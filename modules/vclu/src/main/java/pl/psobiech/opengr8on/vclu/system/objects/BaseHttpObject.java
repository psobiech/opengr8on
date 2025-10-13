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

package pl.psobiech.opengr8on.vclu.system.objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.Headers;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.ServerVersion;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class BaseHttpObject extends VirtualObject {
    protected static final String METHOD_GET = "GET";

    protected static final String HEADER_CONTENT_TYPE = "Content-Type";

    protected static final String HEADER_ACCEPT = "Accept";

    protected static final int CONNECT_TIMEOUT_MILLIS = 4000;

    protected static final String USER_AGENT_HEADER = "User-Agent";

    protected static final String VERSION_INFO = "OpenGr8ton/" + ServerVersion.get();

    protected static final String ROOT_NAME = "root";

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseHttpObject.class);

    protected BaseHttpObject(
            VirtualSystem virtualSystem,
            String name,
            Class<? extends Enum<? extends IFeature>> featureClass,
            Class<? extends Enum<? extends IMethod>> methodClass,
            Class<? extends Enum<? extends IEvent>> eventClass
    ) {
        super(virtualSystem, name, featureClass, methodClass, eventClass);
    }

    protected static LuaValue parseResponseBody(HttpType responseType, Path responseBody) {
        try {
            // TODO: does it make sense to check for filesize?
            return switch (responseType) {
                case NONE -> LuaValue.NIL;
                case JSON -> LuaUtil.fromJson(ObjectMapperFactory.JSON.readTree(responseBody.toFile()));
                case XML -> LuaUtil.fromJson(ObjectMapperFactory.XML.readTree(responseBody.toFile()));
                case FORM_DATA -> LuaUtil.fromObject(urlDecode(Files.readString(responseBody, StandardCharsets.UTF_8)));
                default -> LuaValue.valueOf(Files.readString(responseBody, StandardCharsets.UTF_8));
            };
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);

            return LuaValue.NIL;
        }
    }

    protected static LuaValue parseResponseBody(HttpType responseType, byte[] responseBody) {
        try {
            // TODO: does it make sense to check for filesize?
            return switch (responseType) {
                case NONE -> LuaValue.NIL;
                case JSON -> LuaUtil.fromJson(ObjectMapperFactory.JSON.readTree(responseBody));
                case XML -> LuaUtil.fromJson(ObjectMapperFactory.XML.readTree(responseBody));
                case FORM_DATA -> LuaUtil.fromObject(urlDecode(new String(responseBody, StandardCharsets.UTF_8)));
                default -> LuaValue.valueOf(new String(responseBody, StandardCharsets.UTF_8));
            };
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);

            return LuaValue.NIL;
        }
    }

    protected static Map<String, String> getHeaders(Headers responseHeaders) {
        final Map<String, String> headers = new HashMap<>();
        for (Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            final List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }

            headers.put(entry.getKey(), values.getFirst());
        }

        return headers;
    }

    protected static Map<String, String> getHeaders(HttpHeaders responseHeaders) {
        final Map<String, String> headers = new HashMap<>();
        for (Entry<String, List<String>> entry : responseHeaders.map().entrySet()) {
            final List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }

            headers.put(entry.getKey(), values.getFirst());
        }

        return headers;
    }

    protected static Map<String, String> urlDecode(String queryParameters) {
        if (queryParameters == null || queryParameters.isEmpty()) {
            return Map.of();
        }

        if (queryParameters.indexOf(0) == '?') {
            queryParameters = queryParameters.substring(1);
        }

        // TODO: multi-value/array support? for now last one wins
        final Map<String, String> map = new HashMap<>();
        final String[] parts = queryParameters.split("&");
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }

            final String[] keyValue = part.split("=", 2);
            if (keyValue.length == 0) {
                map.put(urlDecodeComponent(part), "");

                continue;
            }

            if (keyValue.length == 1) {
                map.put(urlDecodeComponent(keyValue[0]), "");

                continue;
            }

            final String key = keyValue[0];
            final String value = keyValue[1];

            map.put(
                    urlDecodeComponent(key),
                    urlDecodeComponent(value)
            );
        }

        return map;
    }

    protected static String urlDecodeComponent(String key) {
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }

    protected static String urlEncode(Map<String, String> map) {
        return Util.stringifyMap(
                map,
                "&", "=",
                BaseHttpObject::urlEncodeComponent,
                BaseHttpObject::urlEncodeComponent
        );
    }

    protected static String urlEncodeComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    protected String stringifyBody(HttpType requestType, LuaValue requestBodyValue) {
        try {
            return switch (requestType) {
                case NONE -> null;
                case JSON -> ObjectMapperFactory.JSON.writeValueAsString(LuaUtil.asObject(requestBodyValue));
                case XML ->
                        ObjectMapperFactory.XML.writer().withRootName(ROOT_NAME).writeValueAsString(LuaUtil.asObject(requestBodyValue));
                case FORM_DATA -> urlEncode(LuaUtil.tableStringString(requestBodyValue));
                default -> requestBodyValue.checkjstring();
            };
        } catch (JsonProcessingException e) {
            throw new UnexpectedException(e);
        }
    }

    protected String getQueryParametersString(LuaValue queryParams) {
        if (LuaUtil.isNil(queryParams)) {
            return "";
        }

        final String queryString;
        if (queryParams.istable()) {
            queryString = urlEncode(LuaUtil.tableStringString(queryParams));
        } else {
            // assume its already url encoded
            queryString = queryParams.checkjstring();
        }

        if (queryString.isEmpty()) {
            return "";
        }

        return "?" + queryString;
    }

    protected enum HttpType {
        NONE(null),
        TEXT("text/plain"),
        JSON("application/json"),
        XML("application/xml"),
        FORM_DATA("application/x-www-form-urlencoded"),
        OTHER(null),
        //
        ;

        private final String contentType;

        HttpType(String contentType) {
            this.contentType = contentType;
        }

        public static HttpType ofContentType(String contentType) {
            if (contentType == null) {
                return NONE;
            }

            for (HttpType value : values()) {
                final String valueContentType = value.contentType();
                if (valueContentType == null) {
                    continue;
                }

                if (contentType.startsWith(valueContentType)) {
                    return value;
                }
            }

            return OTHER;
        }

        public String contentType() {
            return contentType;
        }
    }
}
