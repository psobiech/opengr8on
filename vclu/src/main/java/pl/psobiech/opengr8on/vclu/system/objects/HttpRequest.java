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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import static org.apache.commons.lang3.StringUtils.upperCase;

public class HttpRequest extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

    public static final int INDEX = 121;

    private static final String METHOD_GET = "GET";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final String HEADER_ACCEPT = "Accept";

    private static final double MAXIMUM_RESPONSE_LENGTH = 10 * 1024; // 10kB

    private static final int CONNECT_TIMEOUT = 4000;

    private HttpURLConnection connection;

    private Future<?> responseFuture;

    public HttpRequest(String name) {
        super(
            name,
            Features.class, Methods.class, Events.class
        );

        register(Features.ACTIVE, () ->
            LuaValue.valueOf(responseFuture != null && !responseFuture.isDone())
        );

        register(Methods.SEND_REQUEST, this::onSendRequest);
        register(Methods.ABORT_REQUEST, this::onAbortRequest);
        register(Methods.CLEAR, this::onClear);
    }

    private LuaValue onSendRequest() {
        onAbortRequest();

        final HttpURLConnection newConnection = createConnection();

        final HttpType requestType = HttpType.values()[get(Features.REQUEST_TYPE).checkint()];
        final LuaValue requestBodyValue = get(Features.REQUEST_BODY);

        this.connection = newConnection;
        this.responseFuture = executor.submit(
            () -> {
                try {
                    final String requestBodyAsString = stringifyBody(requestType, requestBodyValue);

                    newConnection.connect();
                    if (newConnection.getDoOutput() && requestBodyAsString != null) {
                        sendString(requestBodyAsString, newConnection);
                    }

                    triggerEvent(Events.REQUEST_SENT);

                    final int statusCode = newConnection.getResponseCode();

                    final HttpType responseType = Optional.ofNullable(newConnection.getHeaderField(HEADER_CONTENT_TYPE))
                                                          .map(HttpType::ofContentType)
                                                          .orElse(HttpType.OTHER);

                    set(Features.RESPONSE_STATUS, LuaValue.valueOf(statusCode));
                    set(Features.RESPONSE_TYPE, LuaValue.valueOf(responseType.ordinal()));
                    set(Features.RESPONSE_HEADERS, LuaUtil.fromObject(getHeaders(newConnection)));

                    final long contentLength = newConnection.getContentLengthLong();
                    if (contentLength > MAXIMUM_RESPONSE_LENGTH) {
                        throw new UnexpectedException("Response was too large: " + contentLength);
                    }

                    final String responseBody;
                    try (InputStream inputStream = new BufferedInputStream(newConnection.getInputStream())) {
                        responseBody = new String(inputStream.readAllBytes());
                    }

                    set(
                        Features.RESPONSE_BODY,
                        parseResponseBody(responseType, responseBody)
                    );

                    triggerEvent(Events.RESPONSE);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);

                    clear(Features.RESPONSE_STATUS);
                    clear(Features.RESPONSE_HEADERS);
                    clear(Features.RESPONSE_BODY);
                }
            }
        );

        return LuaValue.NIL;
    }

    private String stringifyBody(HttpType requestType, LuaValue requestBodyValue) throws JsonProcessingException {
        return switch (requestType) {
            case NONE -> null;
            case JSON -> ObjectMapperFactory.JSON.writeValueAsString(LuaUtil.asObject(requestBodyValue));
            case XML -> ObjectMapperFactory.XML.writeValueAsString(LuaUtil.asObject(requestBodyValue));
            case FORM_DATA -> urlEncode(LuaUtil.tableStringString(requestBodyValue));
            default -> requestBodyValue.checkjstring();
        };
    }

    private static void sendString(String value, HttpURLConnection newConnection) throws IOException {
        try (OutputStream outputStream = new BufferedOutputStream(newConnection.getOutputStream())) {
            outputStream.write(
                value
                    .getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    private static LuaValue parseResponseBody(HttpType responseType, String responseBody) throws JsonProcessingException {
        return switch (responseType) {
            case NONE -> LuaValue.NIL;
            case JSON -> LuaUtil.fromJson(ObjectMapperFactory.JSON.readTree(responseBody));
            case XML -> LuaUtil.fromJson(ObjectMapperFactory.XML.readTree(responseBody));
            case FORM_DATA -> LuaUtil.fromObject(urlDecode(responseBody));
            default -> LuaValue.valueOf(responseBody);
        };
    }

    private static Map<String, String> getHeaders(HttpURLConnection newConnection) {
        final Map<String, String> headers = new HashMap<>();
        for (Entry<String, List<String>> entry : newConnection.getHeaderFields().entrySet()) {
            final List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }

            final String value = values.getFirst();

            headers.put(entry.getKey(), value);
        }

        return headers;
    }

    private HttpURLConnection createConnection() {
        try {
            final String host = get(Features.HOST).checkjstring();
            final String path = get(Features.PATH).checkjstring();
            final String queryParamsAsString = getQueryParametersString();

            final URI uri = URI.create(host + path + queryParamsAsString);
            final URLConnection urlConnection = uri.toURL().openConnection();
            if (!(urlConnection instanceof final HttpURLConnection newConnection)) {
                throw new UnexpectedException("Unsupported URL: %s".formatted(urlConnection.getClass()));
            }

            final long timeoutMillis = TimeUnit.SECONDS.toMillis(get(Features.TIMEOUT).checklong());
            newConnection.setConnectTimeout(CONNECT_TIMEOUT); // TODO: what would be a good value here?
            newConnection.setReadTimeout((int) timeoutMillis);

            final String requestMethod = upperCase(get(Features.METHOD).checkjstring());
            newConnection.setDoOutput(!requestMethod.equals(METHOD_GET));
            newConnection.setDoInput(true);

            newConnection.setRequestMethod(requestMethod);

            final HttpType responseType = HttpType.values()[get(Features.RESPONSE_TYPE).checkint()];
            final String responseContentType = responseType.contentType();
            if (responseContentType != null) {
                newConnection.setRequestProperty(HEADER_ACCEPT, responseContentType);
            }

            final Map<String, String> headers = LuaUtil.tableStringString(get(Features.REQUEST_HEADERS));
            for (Entry<String, String> entry : headers.entrySet()) {
                newConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            final HttpType requestType = HttpType.values()[get(Features.REQUEST_TYPE).checkint()];
            final String requestContentType = requestType.contentType();
            if (requestContentType != null) {
                newConnection.setRequestProperty(HEADER_CONTENT_TYPE, requestContentType);
            }

            return newConnection;
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private static Map<String, String> urlDecode(String queryParameters) {
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

    private static String urlDecodeComponent(String key) {
        return URLDecoder.decode(key, StandardCharsets.UTF_8);
    }

    private String getQueryParametersString() {
        final LuaValue luaValue = get(Features.QUERY);
        if (LuaUtil.isNil(luaValue)) {
            return "";
        }

        final String queryString;
        if (luaValue.istable()) {
            queryString = urlEncode(LuaUtil.tableStringString(luaValue));
        } else {
            // assume its already url encoded
            queryString = luaValue.checkjstring();
        }

        if (queryString.isEmpty()) {
            return "";
        }

        return "?" + queryString;
    }

    private static String urlEncode(Map<String, String> map) {
        return Util.stringifyMap(
            map,
            "&", "=",
            HttpRequest::urlEncodeComponent,
            HttpRequest::urlEncodeComponent
        );
    }

    private static String urlEncodeComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private LuaValue onAbortRequest() {
        ThreadUtil.cancel(responseFuture);
        if (connection != null) {
            connection.disconnect();
        }

        responseFuture = null;
        connection     = null;

        return LuaValue.NIL;
    }

    private LuaValue onClear() {
        // TODO: which exactly features to clear?
        clear(Features.REQUEST_BODY);
        clear(Features.REQUEST_HEADERS);
        clear(Features.REQUEST_TYPE);

        clear(Features.RESPONSE_STATUS);
        clear(Features.RESPONSE_HEADERS);
        clear(Features.RESPONSE_BODY);

        return LuaValue.NIL;
    }

    @Override
    public void close() {
        super.close();

        if (connection != null) {
            connection.disconnect();
        }

        ThreadUtil.cancel(responseFuture);
    }

    private enum HttpType {
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

        public String contentType() {
            return contentType;
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
        RESPONSE_STATUS(11),
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
}
