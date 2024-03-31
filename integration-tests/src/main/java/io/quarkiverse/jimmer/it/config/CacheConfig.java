package io.quarkiverse.jimmer.it.config;

import java.time.Duration;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.AbstractCacheFactory;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.chain.ChainCacheBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.it.entity.BookStoreProps;
import io.quarkiverse.jimmer.runtime.cache.CaffeineBinder;
import io.quarkiverse.jimmer.runtime.cache.RedisCaches;
import io.quarkiverse.jimmer.runtime.cache.RedisHashBinder;
import io.quarkiverse.jimmer.runtime.cache.RedisValueBinder;
import io.quarkus.arc.Unremovable;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

@ApplicationScoped
public class CacheConfig {

    @Singleton
    @Unremovable
    public CacheFactory cacheFactory(RedisDataSource redisDataSource, ObjectMapper objectMapper) {

        ValueCommands<String, byte[]> stringValueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);

        HashCommands<String, String, byte[]> stringHashCommands = RedisCaches.cacheRedisHashCommands(redisDataSource);

        return new AbstractCacheFactory() {
            @Override
            public Cache<?, ?> createObjectCache(ImmutableType type) {
                return new ChainCacheBuilder<>()
                        .add(new CaffeineBinder<>(512, Duration.ofSeconds(1)))
                        .add(new RedisValueBinder<>(stringValueCommands, objectMapper, type, Duration.ofMinutes(10)))
                        .build();

            }

            @Override
            public Cache<?, ?> createAssociatedIdCache(ImmutableProp prop) {
                return createPropCache(
                        getFilterState().isAffected(prop.getTargetType()),
                        prop,
                        stringValueCommands,
                        stringHashCommands,
                        objectMapper,
                        Duration.ofMinutes(5));
            }

            @Override
            public Cache<?, List<?>> createAssociatedIdListCache(ImmutableProp prop) {
                return createPropCache(
                        getFilterState().isAffected(prop.getTargetType()),
                        prop,
                        stringValueCommands,
                        stringHashCommands,
                        objectMapper,
                        Duration.ofMinutes(5));
            }

            @Override
            public Cache<?, ?> createResolverCache(ImmutableProp prop) {
                return createPropCache(
                        prop.equals(BookStoreProps.AVG_PRICE.unwrap()) ||
                                prop.equals(BookStoreProps.NEWEST_BOOKS.unwrap()),
                        prop,
                        stringValueCommands,
                        stringHashCommands,
                        objectMapper,
                        Duration.ofHours(1));
            }
        };
    }

    private static <K, V> Cache<K, V> createPropCache(
            boolean isMultiView,
            ImmutableProp prop,
            ValueCommands<String, byte[]> stringValueCommands,
            HashCommands<String, String, byte[]> stringHashCommands,
            ObjectMapper objectMapper,
            Duration redisDuration) {
        if (isMultiView) {
            return new ChainCacheBuilder<K, V>()
                    .add(new RedisHashBinder<>(stringHashCommands, stringValueCommands, objectMapper, prop, redisDuration))
                    .build();
        }

        return new ChainCacheBuilder<K, V>()
                .add(new CaffeineBinder<>(512, Duration.ofSeconds(1)))
                .add(new RedisValueBinder<>(stringValueCommands, objectMapper, prop, redisDuration))
                .build();
    }
}
