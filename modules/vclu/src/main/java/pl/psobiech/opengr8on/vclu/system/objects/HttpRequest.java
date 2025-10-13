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

import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.util.FileUtil;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.util.ThreadUtil;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Future;

import static org.apache.commons.lang3.StringUtils.upperCase;

public class HttpRequest extends BaseHttpObject {
    public static final int INDEX = 121;

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);

    private final HttpClient httpClient;

    private Future<HttpResponse<Path>> httpFuture;

    private Future<?> responseFuture;

    public HttpRequest(VirtualSystem virtualSystem, String name, Inet4Address localAddress) {
        super(
                virtualSystem, name,
                Features.class, Methods.class, Events.class
        );

        this.httpClient = HttpClient.newBuilder()
                                    .localAddress(localAddress)
                                    .proxy(ProxySelector.getDefault())
                                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_NONE))
                                    .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
                                    .executor(scheduler)
                                    .version(Version.HTTP_2)
                                    .build();

        register(Features.ACTIVE, () ->
                LuaValue.valueOf(responseFuture != null && !responseFuture.isDone())
        );

        register(Methods.SEND_REQUEST, this::onSendRequest);
        register(Methods.ABORT_REQUEST, this::onAbortRequest);
        register(Methods.CLEAR, this::onClear);
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

    private LuaValue onSendRequest(LuaValue bodyArg) {
        awaitEventTrigger(Events.REQUEST_SENT);
        awaitEventTrigger(Events.RESPONSE);

        onAbortRequest();

        final HttpType requestType = HttpType.values()[get(Features.REQUEST_TYPE).checkint()];
        final String requestBodyAsString = stringifyBody(requestType, LuaUtil.isNil(bodyArg) ? get(Features.REQUEST_BODY) : bodyArg);

        final java.net.http.HttpRequest request = createRequest(requestType, requestBodyAsString);

        this.responseFuture = scheduler.submit(
                () -> {
                    final Path temporaryFile = FileUtil.temporaryFile();
                    try {
                        LOGGER.trace("HTTP Request {} {} / BODY: {}", request.method(), request.uri(), requestBodyAsString);

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
                    } catch (Exception e) {
                        LOGGER.error("HTTP Error {} {}", request.method(), request.uri(), e);

                        clear(Features.RESPONSE_STATUS);
                        clear(Features.RESPONSE_HEADERS);
                        clear(Features.RESPONSE_BODY);
                    } finally {
                        FileUtil.deleteQuietly(temporaryFile);
                    }
                }
        );

        return LuaValue.NIL;
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
