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

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.Inet4Address;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Future;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ObjectMapperFactory;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.util.Util;
import pl.psobiech.opengr8on.vclu.ServerVersion;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import static org.apache.commons.lang3.StringUtils.upperCase;

public class HttpRequest extends VirtualObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

    public static final int INDEX = 121;

    private static final String METHOD_GET = "GET";

    private static final String HEADER_CONTENT_TYPE = "Content-Type";

    private static final String HEADER_ACCEPT = "Accept";

    private static final int CONNECT_TIMEOUT_MILLIS = 4000;

    private static final String USER_AGENT_HEADER = "User-Agent";

    private static final String VERSION_INFO = "OpenGr8ton/" + ServerVersion.get();

    private final HttpClient httpClient;

    private Future<HttpResponse<Path>> httpFuture;

    private Future<?> responseFuture;

    public HttpRequest(String name, Inet4Address localAddress) {
        super(
            name,
            Features.class, Methods.class, Events.class
        );

        this.httpClient = HttpClient.newBuilder()
                                    .localAddress(localAddress)
                                    .proxy(ProxySelector.getDefault())
                                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_NONE))
                                    .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
                                    .executor(executor)
                                    .version(Version.HTTP_2)
                                    .build();

        register(Features.ACTIVE, () ->
            LuaValue.valueOf(responseFuture != null && !responseFuture.isDone())
        );

        register(Methods.SEND_REQUEST, this::onSendRequest);
        register(Methods.ABORT_REQUEST, this::onAbortRequest);
        register(Methods.CLEAR, this::onClear);
    }

    private LuaValue onSendRequest(LuaValue bodyArg) {
        onAbortRequest();

        final HttpType requestType = HttpType.values()[get(Features.REQUEST_TYPE).checkint()];
        final String requestBodyAsString = stringifyBody(requestType, LuaUtil.isNil(bodyArg) ? get(Features.REQUEST_BODY) : bodyArg);

        final java.net.http.HttpRequest request = createRequest(requestType, requestBodyAsString);

        this.responseFuture = executor.submit(
            () -> {
                try {
                    LOGGER.trace("HTTP Request {} {} / BODY: {}", request.method(), request.uri(), requestBodyAsString);

                    final Path temporaryFile = FileUtil.temporaryFile();
                    try {
                        this.httpFuture = httpClient.sendAsync(request, BodyHandlers.ofFile(temporaryFile));

                        triggerEvent(Events.REQUEST_SENT);

                        final HttpResponse<Path> response = httpFuture.get();
                        final int statusCode = response.statusCode();
                        final HttpHeaders headers = response.headers();
                        final HttpType responseType = headers.firstValue(HEADER_CONTENT_TYPE)
                                                             .map(HttpType::ofContentType)
                                                             .orElse(HttpType.OTHER);

                        set(Features.RESPONSE_STATUS, LuaValue.valueOf(statusCode));
                        set(Features.RESPONSE_TYPE, LuaValue.valueOf(responseType.ordinal()));
                        set(Features.RESPONSE_HEADERS, LuaUtil.fromObject(getHeaders(headers)));
                        final LuaValue responseBodyValue = parseResponseBody(responseType, response.body());
                        set(Features.RESPONSE_BODY, responseBodyValue);

                        LOGGER.trace("HTTP Response {} {} / BODY: {}", request.method(), request.uri(), LuaUtil.stringifyRaw(responseBodyValue));

                        triggerEvent(Events.RESPONSE);
                    } finally {
                        FileUtil.deleteQuietly(temporaryFile);
                    }
                } catch (Exception e) {
                    LOGGER.error("HTTP Error {} {}", request.method(), request.uri(), e);

                    clear(Features.RESPONSE_STATUS);
                    clear(Features.RESPONSE_HEADERS);
                    clear(Features.RESPONSE_BODY);
                }
            }
        );

        return LuaValue.NIL;
    }

    private String stringifyBody(HttpType requestType, LuaValue requestBodyValue) {
        try {
            return switch (requestType) {
                case NONE -> null;
                case JSON -> ObjectMapperFactory.JSON.writeValueAsString(LuaUtil.asObject(requestBodyValue));
                case XML -> ObjectMapperFactory.XML.writeValueAsString(LuaUtil.asObject(requestBodyValue));
                case FORM_DATA -> urlEncode(LuaUtil.tableStringString(requestBodyValue));
                default -> requestBodyValue.checkjstring();
            };
        } catch (JsonProcessingException e) {
            throw new UnexpectedException(e);
        }
    }

    private static LuaValue parseResponseBody(HttpType responseType, Path responseBody) {
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

    private static Map<String, String> getHeaders(HttpHeaders responseHeaders) {
        final Map<String, String> headers = new HashMap<>();
        for (Entry<String, List<String>> entry : responseHeaders.map().entrySet()) {
            final List<String> values = entry.getValue();
            if (values.isEmpty()) {
                continue;
            }

            final String value = values.getFirst();

            headers.put(entry.getKey(), value);
        }

        return headers;
    }

    private java.net.http.HttpRequest createRequest(HttpType requestType, String requestBodyAsString) {
        final String host = get(Features.HOST).checkjstring();
        final String path = get(Features.PATH).checkjstring();
        final String queryParamsAsString = getQueryParametersString(get(Features.QUERY));
        final URI uri = URI.create(host + path + queryParamsAsString);

        final Duration timeout = Duration.ofSeconds(get(Features.TIMEOUT).checklong());
        final String requestMethod = upperCase(get(Features.METHOD).checkjstring());

        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put(USER_AGENT_HEADER, VERSION_INFO);
        final HttpType responseType = HttpType.values()[get(Features.RESPONSE_TYPE).checkint()];
        final String responseContentType = responseType.contentType();
        if (responseContentType != null) {
            headers.put(HEADER_ACCEPT, responseContentType);
        }

        final String requestContentType = requestType.contentType();
        if (requestContentType != null) {
            headers.put(HEADER_CONTENT_TYPE, requestContentType);
        }

        headers.putAll(LuaUtil.tableStringString(get(Features.REQUEST_HEADERS)));

        set(Features.REQUEST_HEADERS, LuaUtil.fromObject(headers));

        return java.net.http.HttpRequest.newBuilder()
                                        .method(
                                            requestMethod,
                                            requestMethod.equals(METHOD_GET) ? BodyPublishers.noBody() : BodyPublishers.ofString(requestBodyAsString)
                                        )
                                        .uri(uri)
                                        .timeout(timeout)
                                        .headers(asKeyValueArray(headers))
                                        .build();
    }

    private static String[] asKeyValueArray(Map<String, String> headers) {
        int i = 0;
        final String[] headerArray = new String[headers.size() * 2];
        for (Entry<String, String> entry : headers.entrySet()) {
            headerArray[i++] = entry.getKey();
            headerArray[i++] = entry.getValue();
        }

        return headerArray;
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

    private String getQueryParametersString(LuaValue queryParams) {
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
        ThreadUtil.cancel(httpFuture);
        ThreadUtil.cancel(responseFuture);

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

        ThreadUtil.cancel(httpFuture);
        ThreadUtil.cancel(responseFuture);

        IOUtil.closeQuietly(httpClient);
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
