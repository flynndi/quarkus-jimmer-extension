package io.quarkiverse.jimmer.it.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import jakarta.inject.Inject;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.RemoteKeyPrefixProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookStore;
import io.quarkiverse.jimmer.it.entity.Immutables;
import io.quarkiverse.jimmer.runtime.cache.RedisHashBinder;
import io.quarkiverse.jimmer.runtime.cache.RedisValueBinder;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;

/** Both legacy binders must work with an omitted or plain mapper, without modifying the caller's mapper. */
@QuarkusTest
class RedisBinderCodecRegressionTestCase {

    @Inject
    RedisDataSource redisDataSource;

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void valueBinderRoundTripsImmutableEntities(boolean supplyMapper) {
        ObjectMapper mapper = new ObjectMapper();
        var modules = Set.copyOf(mapper.getRegisteredModuleIds());
        var serializationConfig = mapper.getSerializationConfig();
        var deserializationConfig = mapper.getDeserializationConfig();
        var builder = RedisValueBinder.<Long, Book> forObject(ImmutableType.get(Book.class))
                .duration(Duration.ofMinutes(1))
                .randomPercent(10)
                .keyPrefixProvider(isolatedKeys())
                .redis(redisDataSource);
        if (supplyMapper) {
            builder.objectMapper(mapper);
        }
        RedisValueBinder<Long, Book> binder = builder.build();
        long id = 1L;
        Book book = Immutables.createBook(draft -> {
            draft.setId(id);
            draft.setName("Effective Java");
            draft.setEdition(3);
            draft.setPrice(new BigDecimal("45.00"));
        });
        try {
            binder.setAll(Map.of(id, book));
            assertEquals(Map.of(id, book), binder.getAll(List.of(id)));
            assertEquals(modules, mapper.getRegisteredModuleIds());
            assertSame(serializationConfig, mapper.getSerializationConfig());
            assertSame(deserializationConfig, mapper.getDeserializationConfig());
        } finally {
            binder.deleteAll(List.of(id), "redis");
        }
        assertTrue(binder.getAll(List.of(id)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void hashBinderRoundTripsAssociationIdsForSeparateParameterMaps(boolean supplyMapper) {
        ObjectMapper mapper = new ObjectMapper();
        var modules = Set.copyOf(mapper.getRegisteredModuleIds());
        var serializationConfig = mapper.getSerializationConfig();
        var deserializationConfig = mapper.getDeserializationConfig();
        var builder = RedisHashBinder.<Long, List<Long>> forProp(ImmutableType.get(BookStore.class).getProp("books"))
                .duration(Duration.ofMinutes(1))
                .randomPercent(10)
                .keyPrefixProvider(isolatedKeys())
                .redis(redisDataSource);
        if (supplyMapper) {
            builder.objectMapper(mapper);
        }
        RedisHashBinder<Long, List<Long>> binder = builder.build();
        long id = 1L;
        var tenantA = new TreeMap<String, Object>(Map.of("tenant", "a"));
        var tenantB = new TreeMap<String, Object>(Map.of("tenant", "b"));
        Map<Long, List<Long>> first = Map.of(id, List.of(11L, 12L));
        Map<Long, List<Long>> second = Map.of(id, List.of(13L));
        try {
            binder.setAll(first, tenantA);
            binder.setAll(second, tenantB);
            assertEquals(first, binder.getAll(List.of(id), tenantA));
            assertEquals(second, binder.getAll(List.of(id), tenantB));
            assertTrue(binder.getAll(List.of(id)).isEmpty());
            assertEquals(modules, mapper.getRegisteredModuleIds());
            assertSame(serializationConfig, mapper.getSerializationConfig());
            assertSame(deserializationConfig, mapper.getDeserializationConfig());
        } finally {
            binder.deleteAll(List.of(id), "redis");
        }
        assertTrue(binder.getAll(List.of(id), tenantA).isEmpty());
        assertTrue(binder.getAll(List.of(id), tenantB).isEmpty());
    }

    private static RemoteKeyPrefixProvider isolatedKeys() {
        String prefix = "quarkus-jimmer-test:" + UUID.randomUUID() + ':';
        return new RemoteKeyPrefixProvider() {
            @Override
            public String typeKeyPrefix(ImmutableType type) {
                return prefix + type.getJavaClass().getSimpleName() + ':';
            }

            @Override
            public String propKeyPrefix(ImmutableProp prop) {
                return prefix + prop.getDeclaringType().getJavaClass().getSimpleName() + '.' + prop.getName() + ':';
            }
        };
    }
}
