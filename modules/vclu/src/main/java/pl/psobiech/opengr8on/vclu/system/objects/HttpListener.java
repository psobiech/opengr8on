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
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.luaj.vm2.LuaValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.psobiech.opengr8on.exceptions.UnexpectedException;
import pl.psobiech.opengr8on.util.IOUtil;
import pl.psobiech.opengr8on.vclu.system.VirtualSystem;
import pl.psobiech.opengr8on.vclu.util.LuaUtil;

public class HttpListener extends BaseHttpObject {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpListener.class);

    public static final int INDEX = 120;

    private static final int STOP_DELAY_SECONDS = 1;

    private static final int HTTP_PORT = 80;

    private static final String HEADER_SERVER = "Server";

    private static final int STATUS_NOT_FOUND = 404;

    private static final int STATUS_INTERNAL_SERVER_ERROR = 500;

    private final HttpServer httpServer;

    private HttpExchange exchange;

    public HttpListener(VirtualSystem virtualSystem, String name, Inet4Address localAddress) {
        super(
                virtualSystem, name,
                Features.class, Methods.class, Events.class
        );

        try {
            this.httpServer = HttpServer.create();
            this.httpServer.bind(new InetSocketAddress(localAddress, HTTP_PORT), 0);

            this.httpServer.createContext(
                    "/",
                    newExchange -> {
                        if (!isEventRegistered(Events.REQUEST)) {
                            tryErrorIfNotClosed(STATUS_NOT_FOUND, newExchange);

                            return;
                        }

                        awaitEventTrigger(Events.REQUEST);
                        IOUtil.closeQuietly(exchange);

                        set(Features.METHOD, LuaValue.valueOf(newExchange.getRequestMethod()));

                        final URI uri = newExchange.getRequestURI();
                        set(Features.PATH, LuaValue.valueOf(uri.getPath()));
                        set(Features.QUERY, LuaUtil.fromObject(urlDecode(uri.getRawQuery())));

                        set(Features.REQUEST_HEADERS, LuaUtil.fromObject(getHeaders(newExchange.getRequestHeaders())));

                        final String contentType = newExchange.getRequestHeaders().getFirst(HEADER_CONTENT_TYPE);
                        final HttpType requestType = HttpType.ofContentType(contentType);
                        set(Features.REQUEST_TYPE, parseResponseBody(requestType, newExchange.getRequestBody().readAllBytes()));

                        this.exchange = newExchange;
                        triggerEvent(
                                Events.REQUEST,
                                () -> tryErrorIfNotClosed(STATUS_NOT_FOUND, newExchange)
                        );
                    }
            );

            this.httpServer.setExecutor(scheduler);
            this.httpServer.start();
        } catch (IOException e) {
            throw new UnexpectedException(e);
        }

        register(Methods.SEND_RESPONSE, this::onSendResponse);
        register(Methods.CLEAR, this::onClear);
    }

    private LuaValue onSendResponse(LuaValue bodyArg) {
        final HttpExchange currentExchange = this.exchange;

        try {
            final HttpType responseType = HttpType.values()[get(Features.RESPONSE_TYPE).checkint()];

            final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            headers.put(HEADER_CONTENT_TYPE, responseType.contentType());
            headers.put(HEADER_SERVER, VERSION_INFO);
            headers.putAll(LuaUtil.tableStringString(get(Features.RESPONSE_HEADERS)));

            final Headers responseHeaders = currentExchange.getResponseHeaders();
            for (Entry<String, String> entry : headers.entrySet()) {
                responseHeaders.add(entry.getKey(), entry.getValue());
            }

            // TODO: create HttpListener v3 to support body as method parameter
            final String responseBodyAsString = stringifyBody(responseType, LuaUtil.isNil(bodyArg) ? get(Features.RESPONSE_BODY) : bodyArg);
            final byte[] responseAsBytes = responseBodyAsString.getBytes(StandardCharsets.UTF_8);

            currentExchange.sendResponseHeaders(get(Features.RESPONSE_STATUS).checkint(), responseAsBytes.length);
            try (OutputStream outputStream = currentExchange.getResponseBody()) {
                outputStream.write(responseAsBytes);
            }
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        } finally {
            tryErrorIfNotClosed(STATUS_INTERNAL_SERVER_ERROR, currentExchange);
        }

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
        tryErrorIfNotClosed(STATUS_INTERNAL_SERVER_ERROR, exchange);
        httpServer.stop(STOP_DELAY_SECONDS);

        super.close();
    }

    private static void tryErrorIfNotClosed(int statusCode, HttpExchange newExchange) {
        if (newExchange == null) {
            return;
        }

        try {
            newExchange.sendResponseHeaders(statusCode, 0);
        } catch (Exception e) {
            // NOP
        }

        IOUtil.closeQuietly(newExchange);
    }

    private enum Features implements IFeature {
        PATH(0),
        METHOD(1),
        QUERY(2),
        REQUEST_TYPE(3),
        REQUEST_HEADERS(4),
        REQUEST_BODY(5),
        RESPONSE_TYPE(6),
        RESPONSE_HEADERS(7),
        RESPONSE_BODY(8),
        RESPONSE_STATUS(9),
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
        SEND_RESPONSE(0),
        CLEAR(1),
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
        REQUEST(0),
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
