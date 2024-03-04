package io.quarkiverse.jimmer.runtime.repository.common;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.quarkiverse.jimmer.runtime.util.Assert;
import io.quarkiverse.jimmer.runtime.util.StreamUtils;

@FunctionalInterface
public interface Streamable<T> extends Iterable<T>, Supplier<Stream<T>> {

    static <T> Streamable<T> empty() {
        return Collections::emptyIterator;
    }

    /**
     * Returns a {@link Streamable} with the given elements.
     *
     * @param t the elements to return.
     * @return
     */
    @SafeVarargs
    static <T> Streamable<T> of(T... t) {
        return () -> Arrays.asList(t).iterator();
    }

    /**
     * Returns a {@link Streamable} for the given {@link Iterable}.
     *
     * @param iterable must not be {@literal null}.
     * @return
     */
    static <T> Streamable<T> of(Iterable<T> iterable) {

        Assert.notNull(iterable, "Iterable must not be null!");

        return iterable::iterator;
    }

    /**
     * Creates a non-parallel {@link Stream} of the underlying {@link Iterable}.
     *
     * @return will never be {@literal null}.
     */
    default Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Returns whether the current {@link Streamable} is empty.
     *
     * @return
     */
    default boolean isEmpty() {
        return !iterator().hasNext();
    }

    /**
     * Creates a new, unmodifiable {@link List}.
     *
     * @return will never be {@literal null}.
     * @since 2.2
     */
    default List<T> toList() {
        return stream().collect(StreamUtils.toUnmodifiableList());
    }

    /**
     * Creates a new, unmodifiable {@link Set}.
     *
     * @return will never be {@literal null}.
     * @since 2.2
     */
    default Set<T> toSet() {
        return stream().collect(StreamUtils.toUnmodifiableSet());
    }

    /*
     * (non-Javadoc)
     *
     * @see java.util.function.Supplier#get()
     */
    default Stream<T> get() {
        return stream();
    }

    /**
     * A collector to easily produce a {@link Streamable} from a {@link Stream} using {@link Collectors#toList} as
     * intermediate collector.
     *
     * @return
     * @see #toStreamable(Collector)
     * @since 2.2
     */
    public static <S> Collector<S, ?, Streamable<S>> toStreamable() {
        return toStreamable(Collectors.toList());
    }

    /**
     * A collector to easily produce a {@link Streamable} from a {@link Stream} and the given intermediate collector.
     *
     * @return
     * @since 2.2
     */
    @SuppressWarnings("unchecked")
    public static <S, T extends Iterable<S>> Collector<S, ?, Streamable<S>> toStreamable(
            Collector<S, ?, T> intermediate) {

        return Collector.of( //
                (Supplier<T>) intermediate.supplier(), //
                (BiConsumer<T, S>) intermediate.accumulator(), //
                (BinaryOperator<T>) intermediate.combiner(), //
                Streamable::of);
    }
}
