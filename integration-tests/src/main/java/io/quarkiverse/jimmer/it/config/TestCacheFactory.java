package io.quarkiverse.jimmer.it.config;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.chain.ChainCacheBuilder;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.jimmer.runtime.cache.CaffeineBinder;
import io.quarkiverse.jimmer.runtime.cache.RedisCaches;
import io.quarkiverse.jimmer.runtime.cache.RedisValueBinder;
import io.quarkus.arc.Unremovable;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;

@ApplicationScoped
public class TestCacheFactory {

    @Singleton
    @Unremovable
    public CacheFactory cacheFactory(RedisDataSource redisDataSource, ObjectMapper objectMapper) {

        ValueCommands<String, byte[]> stringValueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);

        return new CacheFactory() {
            @Override
            @Nullable
            public Cache<?, ?> createObjectCache(ImmutableType type) {
                return new ChainCacheBuilder<>()
                        .add(new CaffeineBinder<>(512, Duration.ofSeconds(1)))
                        .add(new RedisValueBinder<>(stringValueCommands, objectMapper, type, Duration.ofMinutes(30)))
                        .build();

            }

            @Override
            @Nullable
            public Cache<?, ?> createAssociatedIdCache(ImmutableProp prop) {
                return createPropCache(prop, Duration.ofMinutes(10));
            }

            @Override
            @Nullable
            public Cache<?, ?> createResolverCache(ImmutableProp prop) {
                return createPropCache(prop, Duration.ofMinutes(5));
            }

            private Cache<?, ?> createPropCache(ImmutableProp prop, Duration duration) {
                return new ChainCacheBuilder<>()
                        .add(new CaffeineBinder<>(512, Duration.ofSeconds(1)))
                        .add(new RedisValueBinder<>(stringValueCommands, objectMapper, prop, duration))
                        .build();
            }
        };
    }
}
