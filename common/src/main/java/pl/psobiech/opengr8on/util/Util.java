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

package pl.psobiech.opengr8on.util;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.round;

public final class Util {
    public static final int ONE_HUNDRED_PERCENT = 100;

    private static final double ONE_HUNDRED_PERCENT_DOUBLE = ONE_HUNDRED_PERCENT;

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

    public static int percentage(long elementCount, long totalElementCount) {
        return (int) max(0, min(ONE_HUNDRED_PERCENT_DOUBLE, round((elementCount * ONE_HUNDRED_PERCENT_DOUBLE) / (double) totalElementCount)));
    }

    /**
     * @return tries to convert a Serializable to Number formatted as string (with 2 decimal places), defaults to {@link String#valueOf(Object)}
     */
    public static String formatNumber(Number value) {
        if (value == null) {
            return null;
        }

        final DecimalFormat scoreDecimalFormat = new DecimalFormat("0.##");

        return scoreDecimalFormat.format(((Number) value).doubleValue());
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

    public static <F, T> List<T> mapNullSafe(List<F> from, Function<F, T> mapper) {
        return mapNullSafeListWithDefault(from, mapper, Collections.emptyList());
    }

    public static <F, T> List<T> mapNullSafeListWithDefault(List<F> from, Function<F, T> mapper, List<T> nullValue) {
        return Optional.ofNullable(from)
                       .map(f ->
                           f.stream()
                            .map(mapper)
                            .collect(Collectors.toList())
                       )
                       .orElse(nullValue);
    }

    public static <T> T nullAsDefault(T value, T defaultValue) {
        return Objects.requireNonNullElse(value, defaultValue);
    }

    public static <T> T nullAsDefaultGet(T value, Supplier<? extends T> defaultValueSupplier) {
        return Objects.requireNonNullElseGet(value, defaultValueSupplier);
    }

    public static <E extends Enum<E>> EnumSet<E> asEnum(List<String> valueList, Class<E> enumClass) {
        if (valueList == null) {
            return null;
        }

        return valueList.stream()
                        .map(valueAsString -> Enum.valueOf(enumClass, valueAsString))
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(enumClass)));
    }

    /**
     * @return lazy initiated singleton (thread safe)
     */
    public static <T> Supplier<T> lazy(Supplier<T> supplier) {
        return cache(supplier);
    }

    /**
     * @return lazy initiated singleton (thread safe)
     */
    public static <T> Supplier<T> cache(Supplier<T> constructor) {
        final AtomicReference<T> reference = new AtomicReference<>();

        return () -> {
            final T currentValue = reference.get();
            if (currentValue != null) {
                return currentValue;
            }

            return reference.updateAndGet(value -> {
                if (value != null) {
                    // new value was already allocated by some other thread between notnull check and here, we preserve the other thread value
                    return value;
                }

                return constructor.get();
            });
        };
    }
}
