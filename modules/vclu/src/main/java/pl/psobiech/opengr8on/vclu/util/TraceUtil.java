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

package pl.psobiech.opengr8on.vclu.util;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import java.util.function.Supplier;

public class TraceUtil {
    private static final OpenTelemetry telemetry;

    static {
        telemetry = GlobalOpenTelemetry.get();
    }

    private TraceUtil() {
        // NOP
    }

    public static void span(Class<?> clazz, Runnable runnable, String... name) {
        span(
                tracer(clazz),
                () -> {
                    runnable.run();

                    return null;
                },
                name
        );
    }

    public static void span(Tracer tracer, Runnable runnable, String... name) {
        span(
                tracer,
                () -> {
                    runnable.run();

                    return null;
                },
                name
        );
    }

    public static <T> T span(Class<?> clazz, Supplier<T> runnable, String... name) {
        return span(tracer(clazz), runnable, name);
    }

    public static <T> T span(Tracer tracer, Supplier<T> runnable, String... name) {
        final Span span = span(tracer, name);
        try {
            return runnable.get();
        } finally {
            span.end();
        }
    }

    public static Span span(Tracer tracer, String... name) {
        return tracer
                .spanBuilder(String.join(".", name))
                .startSpan();
    }

    public static Tracer tracer(Class<?> clazz) {
        return telemetry.tracerBuilder(clazz.getName())
                        .build();
    }
}
