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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.VirtualObject;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import static org.apache.commons.lang3.StringUtils.upperCase;

public class HttpRequest extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

    private static final String METHOD_GET = "GET";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final double MAXIMUM_RESPONSE_LENGTH = 10 * 1024; // 10kB

    private final ScheduledExecutorService executors;

    private HttpURLConnection connection;

    private ScheduledFuture<?> responseFuture;

    public HttpRequest(String name) {
        super(name);

        executors = ThreadUtil.executor(name);

        register(Features.ACTIVE, arg1 ->
            LuaValue.valueOf(responseFuture != null && !responseFuture.isDone())
        );

        register(Methods.SEND_REQUEST, this::onSendRequest);
        register(Methods.ABORT_REQUEST, this::onAbortRequest);
        register(Methods.CLEAR, this::onClear);
    }

    private LuaValue onSendRequest(LuaValue arg1) {
        final HttpURLConnection newConnection = createConnection();

        this.connection = newConnection;
        this.responseFuture = executors.schedule(
            () -> {
                try {
                    final int statusCode = newConnection.getResponseCode();
                    final long contentLength = newConnection.getContentLengthLong();
                    if (contentLength > MAXIMUM_RESPONSE_LENGTH) {
                        throw new UnexpectedException("Response was too large: " + contentLength);
                    }

                    final Map<String, List<String>> responseHeaders = newConnection.getHeaderFields();
                    final HttpType responseType = Optional.ofNullable(responseHeaders.get(HEADER_CONTENT_TYPE))
                                                          .filter(strings -> !strings.isEmpty())
                                                          .map(List::getFirst)
                                                          .map(HttpType::ofContentType)
                                                          .orElse(HttpType.OTHER);

                    final String responseBody;
                    try (InputStream inputStream = new BufferedInputStream(newConnection.getInputStream())) {
                        responseBody = new String(inputStream.readAllBytes());
                    }

                    set(Features.RESPONSE_STATUS, LuaValue.valueOf(statusCode));
                    set(Features.RESPONSE_TYPE, LuaValue.valueOf(responseType.ordinal()));
                    set(Features.RESPONSE_HEADERS, asTable(responseHeaders));

                    // TODO: deserialize xml/json to tables
                    set(Features.RESPONSE_BODY, LuaValue.valueOf(responseBody));

                    triggerEvent(Events.RESPONSE);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage(), e);
                }
            },
            0, TimeUnit.SECONDS
        );

        triggerEvent(Events.REQUEST_SENT);

        return LuaValue.NIL;
    }

    private static LuaTable asTable(Map<String, List<String>> responseHeaders) {
        final LuaTable luaTable = LuaTable.tableOf();

        for (Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            final String key = entry.getKey();
            if (key == null) {
                continue;
            }

            final List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }

            final String value = values.getFirst();
            luaTable.set(key, value);
        }

        return luaTable;
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
            newConnection.setConnectTimeout(8000); // TODO: what would be a good value here?
            newConnection.setReadTimeout((int) timeoutMillis);

            final String requestMethod = upperCase(get(Features.METHOD).checkjstring());
            newConnection.setDoOutput(!requestMethod.equals(METHOD_GET));
            newConnection.setDoInput(true);

            newConnection.setRequestMethod(requestMethod);

            final HttpType requestType = HttpType.values()[get(Features.REQUEST_TYPE).checkint()];
            if (requestType != HttpType.NONE) {
                newConnection.setRequestProperty(HEADER_CONTENT_TYPE, requestType.contentType());
            }

            final Map<String, String> headers = LuaUtil.tableStringString(get(Features.REQUEST_HEADERS));
            for (Entry<String, String> entry : headers.entrySet()) {
                newConnection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            newConnection.connect();
            if (newConnection.getDoOutput()) {
                // TODO: maybe serialize to json/xml when we detect table
                final String requestBody = get(Features.REQUEST_BODY).checkjstring();
                try (OutputStream outputStream = new BufferedOutputStream(newConnection.getOutputStream())) {
                    outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            return newConnection;
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }
    }

    private String getQueryParametersString() {
        final LuaValue luaValue = get(Features.QUERY);
        if (luaValue == null || luaValue.isnil()) {
            return "";
        }

        if (luaValue.istable()) {
            final Map<String, String> table = LuaUtil.tableStringString(luaValue);

            final StringBuilder sb = new StringBuilder();
            for (Entry<String, String> entry : table.entrySet()) {
                if (!sb.isEmpty()) {
                    sb.append("&");
                }

                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }

            if (sb.isEmpty()) {
                return "";
            }

            return "?" + sb;
        }

        final String queryString = luaValue.checkjstring();
        if (queryString.isEmpty()) {
            return "";
        }

        return "?" + queryString;
    }

    private LuaValue onAbortRequest(LuaValue arg1) {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }

        if (responseFuture != null) {
            responseFuture.cancel(true);
            responseFuture = null;
        }

        return LuaValue.NIL;
    }

    private LuaValue onClear(LuaValue arg1) {
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
        if (connection != null) {
            connection.disconnect();
        }

        if (responseFuture != null) {
            responseFuture.cancel(true);
        }

        executors.shutdown();
    }

    private enum HttpType {
        NONE(null),
        TEXT("text/plain"),
        JSON("application/json"),
        XML("application/xml"),
        FORM_DATA("multipart/form-data"),
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
