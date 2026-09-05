package io.quarkiverse.jimmer.it.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import jakarta.inject.Inject;

import org.babyfish.jimmer.meta.ImmutableType;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.Immutables;
import io.quarkiverse.jimmer.runtime.cache.RedisHashBinder;
import io.quarkiverse.jimmer.runtime.cache.RedisValueBinder;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Regression coverage for the two {@link RedisValueBinder} / {@link RedisHashBinder} builder entry points:
 * an omitted {@link ObjectMapper} (must not NPE during construction, unlike before this was fixed) and a
 * plain, unconfigured {@link ObjectMapper} (must still serialize immutable entities correctly, and must not
 * be mutated by the binder).
 */
@QuarkusTest
class RedisBinderCodecRegressionTestCase {

    @Inject
    RedisDataSource redisDataSource;

    @Test
    void valueBinderBuildsAndRoundTripsWhenObjectMapperOmitted() {
        RedisValueBinder<Long, Book> binder = RedisValueBinder.<Long, Book> forObject(ImmutableType.get(Book.class))
                .duration(Duration.ofMinutes(1))
                .randomPercent(10)
                .redis(redisDataSource)
                .build();

        assertRoundTrips(binder, 90_001L);
    }

    @Test
    void valueBinderBuildsAndRoundTripsWithPlainObjectMapper() {
        ObjectMapper plainMapper = new ObjectMapper();
        int modulesBefore = plainMapper.getRegisteredModuleIds().size();

        RedisValueBinder<Long, Book> binder = RedisValueBinder.<Long, Book> forObject(ImmutableType.get(Book.class))
                .objectMapper(plainMapper)
                .duration(Duration.ofMinutes(1))
                .randomPercent(10)
                .redis(redisDataSource)
                .build();

        assertRoundTrips(binder, 90_002L);
        // the caller's mapper must not be mutated in place
        assertEquals(modulesBefore, plainMapper.getRegisteredModuleIds().size());
    }

    @Test
    void hashBinderBuildsWhenObjectMapperOmitted() {
        RedisHashBinder<Long, Long> binder = RedisHashBinder.<Long, Long> forProp(
                ImmutableType.get(BookStore.class).getProp("books"))
                .duration(Duration.ofMinutes(1))
                .randomPercent(10)
                .redis(redisDataSource)
                .build();

        assertNotNull(binder);
    }

    @Test
    void hashBinderBuildsWithPlainObjectMapper() {
        ObjectMapper plainMapper = new ObjectMapper();

        RedisHashBinder<Long, Long> binder = RedisHashBinder.<Long, Long> forProp(
                ImmutableType.get(BookStore.class).getProp("books"))
                .objectMapper(plainMapper)
                .duration(Duration.ofMinutes(1))
                .randomPercent(10)
                .redis(redisDataSource)
                .build();

        assertNotNull(binder);
        assertFalse(plainMapper.getRegisteredModuleIds().stream()
                .anyMatch(id -> String.valueOf(id).contains("Immutable")));
    }

    private void assertRoundTrips(RedisValueBinder<Long, Book> binder, long id) {
        Book book = Immutables.createBook(draft -> {
            draft.setId(id);
            draft.setName("Effective Java");
            draft.setEdition(3);
            draft.setPrice(new BigDecimal("45.00"));
        });

        binder.setAll(Collections.singletonMap(id, book));
        Map<Long, Book> loaded = binder.getAll(Collections.singletonList(id));

        assertEquals(book, loaded.get(id));
    }
}
