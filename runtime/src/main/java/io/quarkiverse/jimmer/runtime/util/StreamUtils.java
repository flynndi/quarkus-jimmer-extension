package io.quarkiverse.jimmer.runtime.util;

import static java.util.stream.Collectors.*;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.jetbrains.annotations.Nullable;

public interface StreamUtils {

    public static <T> Stream<T> createStreamFromIterator(Iterator<T> iterator) {

        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.NONNULL);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Returns a {@link Collector} to create an unmodifiable {@link List}.
     *
     * @return will never be {@literal null}.
     */
    public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
        return collectingAndThen(toList(), Collections::unmodifiableList);
    }

    /**
     * Returns a {@link Collector} to create an unmodifiable {@link Set}.
     *
     * @return will never be {@literal null}.
     */
    public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
        return collectingAndThen(toSet(), Collections::unmodifiableSet);
    }

    /**
     * Creates a new {@link Stream} for the given value returning an empty {@link Stream} if the value is {@literal null}.
     *
     * @param source can be {@literal null}.
     * @return a new {@link Stream} for the given value returning an empty {@link Stream} if the value is {@literal null}.
     * @since 2.0.6
     */
    public static <T> Stream<T> fromNullable(@Nullable T source) {
        return source == null ? Stream.empty() : Stream.of(source);
    }

}
