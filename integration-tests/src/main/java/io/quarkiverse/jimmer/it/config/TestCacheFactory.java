package io.quarkiverse.jimmer.it.config;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.chain.ChainCacheBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.runtime.cache.CaffeineBinder;
import io.quarkus.arc.Unremovable;

@ApplicationScoped
public class TestCacheFactory {

    @Singleton
    @Unremovable
    public CacheFactory cacheFactory() {
        return new CacheFactory() {
            @Override
            @Nullable
            public Cache<?, ?> createObjectCache(@NotNull ImmutableType type) {
                return new ChainCacheBuilder<>()
                        .add(new CaffeineBinder<>(512, Duration.ofSeconds(1))).build();
            }
        };
    }
}
