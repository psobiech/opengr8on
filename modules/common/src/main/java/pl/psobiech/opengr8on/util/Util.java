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

package pl.psobiech.opengr8on.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import pl.psobiech.opengr8on.exceptions.UncheckedInterruptedException;

public final class Util {
    private static final long NANOS_IN_MILLISECOND = TimeUnit.MILLISECONDS.toNanos(1);

    private Util() {
        // NOP
    }

    public static Optional<Boolean> repeatUntilTrueOrTimeout(Duration timeout, Function<Duration, Optional<Boolean>> fn) {
        Optional<Boolean> optionalBoolean;
        do {
            final long startedAt = System.nanoTime();

            optionalBoolean = fn.apply(timeout);
            if (optionalBoolean.isPresent() && optionalBoolean.get()) {
                return optionalBoolean;
            }

            timeout = timeout.minusNanos(System.nanoTime() - startedAt);
        } while (timeout.isPositive() && !Thread.interrupted());

        return optionalBoolean;
    }

    public static <T> Optional<T> repeatUntilTimeout(Duration timeout, Function<Duration, Optional<T>> fn) {
        return repeatUntilTimeoutOrLimit(timeout, 1, fn).stream().findAny();
    }

    public static <T> List<T> repeatUntilTimeoutOrLimit(Duration timeout, int limit, Function<Duration, Optional<T>> fn) {
        final ArrayList<T> results = new ArrayList<>(Math.max(limit == Integer.MAX_VALUE ? -1 : limit, 0));
        do {
            final long startedAt = System.nanoTime();

            fn.apply(timeout)
              .ifPresent(results::add);

            timeout = timeout.minusNanos(System.nanoTime() - startedAt);
        } while (timeout.isPositive() && results.size() < limit && !Thread.interrupted());

        return results;
    }

    public static Optional<String[]> splitExact(String value, String pattern, int exactlyParts) {
        final String[] split = value.split(pattern, exactlyParts + 1);
        if (split.length != exactlyParts) {
            return Optional.empty();
        }

        return Optional.of(split);
    }

    public static Optional<String[]> splitAtLeast(String value, String pattern, int atLeastParts) {
        final String[] split = value.split(pattern, atLeastParts + 1);
        if (split.length < atLeastParts) {
            return Optional.empty();
        }

        return Optional.of(split);
    }

    public static <E> List<E> nullAsEmpty(List<E> list) {
        if (list == null) {
            return Collections.emptyList();
        }

        return list;
    }

    public static <K, V> Map<K, V> nullAsEmpty(Map<K, V> map) {
        if (map == null) {
            return Collections.emptyMap();
        }

        return map;
    }

    public static <E> Set<E> nullAsEmpty(Set<E> list) {
        if (list == null) {
            return Collections.emptySet();
        }

        return list;
    }

    public static <E> Collection<E> nullAsEmpty(Collection<E> list) {
        if (list == null) {
            return Collections.emptyList();
        }

        return list;
    }

    public static <F, T> T mapNullSafe(F from, Function<F, T> mapper) {
        return mapNullSafeWithDefault(from, mapper, null);
    }

    public static <F, T> T mapNullSafeWithDefault(F from, Function<F, T> mapper, T nullValue) {
        if (from != null) {
            final T value = mapper.apply(from);
            if (value != null) {
                return value;
            }
        }

        return nullValue;
    }

    public static <E extends Enum<E>> EnumSet<E> asEnum(List<String> valueList, Class<E> enumClass) {
        if (valueList == null) {
            return null;
        }

        return valueList.stream()
                        .map(valueAsString -> Enum.valueOf(enumClass, valueAsString))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(enumClass)));
    }

    public static <T> String stringifyList(List<T> list, String delimiter, Function<T, String> toString) {
        final StringBuilder sb = new StringBuilder();
        for (T value : list) {
            if (!sb.isEmpty()) {
                sb.append(delimiter);
            }

            sb.append(toString.apply(value));
        }

        return sb.toString();
    }

    public static <V1, V2> String stringifyMap(
        Map<V1, V2> map,
        String delimiter, String entryDelimiter,
        Function<V1, String> toStringKey, Function<V2, String> toStringValue
    ) {
        final StringBuilder sb = new StringBuilder();
        for (Entry<V1, V2> entry : map.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(delimiter);
            }

            sb.append(toStringKey.apply(entry.getKey()))
              .append(entryDelimiter)
              .append(toStringValue.apply(entry.getValue()));
        }

        return sb.toString();
    }

    public static void sleepNanos(long nanoSeconds) {
        final long millis = nanoSeconds / NANOS_IN_MILLISECOND;
        final int nanos = (int) (nanoSeconds % NANOS_IN_MILLISECOND);

        try {
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }
}
