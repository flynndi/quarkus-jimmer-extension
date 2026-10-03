package io.quarkiverse.jimmer.runtime.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.babyfish.jimmer.sql.cache.RemoteKeyPrefixProvider;
import org.babyfish.jimmer.sql.cache.spi.AbstractRemoteHashBinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;

@Deprecated
public class RedisHashBinder<K, V> extends AbstractRemoteHashBinder<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisHashBinder.class);

    private final HashCommands<String, String, byte[]> hashCommands;

    private final KeyCommands<String> keyCommands;

    protected RedisHashBinder(
            @Nullable ImmutableType type,
            @Nullable ImmutableProp prop,
            @Nullable CacheTracker tracker,
            @Nullable ObjectMapper objectMapper,
            @Nullable RemoteKeyPrefixProvider keyPrefixProvider,
            @NotNull Duration duration,
            int randomPercent,
            @NotNull RedisDataSource redisDataSource) {
        super(type, prop, tracker, RedisValueBinder.toCodec(objectMapper), keyPrefixProvider, duration, randomPercent);
        this.hashCommands = redisDataSource.hash(byte[].class);
        this.keyCommands = redisDataSource.key();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected List<byte[]> read(Collection<String> keys, String hashKey) {
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<byte[]> list = new ArrayList<>();
        for (String key : keys) {
            list.add(hashCommands.hget(key, hashKey));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void write(Map<String, byte[]> map, String hashKey) {
        for (Map.Entry<String, byte[]> e : map.entrySet()) {
            hashCommands.hset(e.getKey(), hashKey, e.getValue());
            keyCommands.pexpire(e.getKey(), nextExpireMillis());
        }
    }

    @Override
    protected void deleteAllSerializedKeys(List<String> serializedKeys) {
        LOGGER.info("Delete data from redis: {}", serializedKeys);
        // Keys can belong to different Redis Cluster slots.
        for (String key : serializedKeys) {
            keyCommands.del(key);
        }
    }

    @Override
    protected boolean matched(@Nullable Object reason) {
        return "redis".equals(reason);
    }

    @NotNull
    public static <K, V> Builder<K, V> forProp(ImmutableProp prop) {
        return new Builder<>(null, prop);
    }

    public static class Builder<K, V> extends AbstractBuilder<K, V, Builder<K, V>> {

        private RedisDataSource redisDataSource;

        private ObjectMapper objectMapper;

        protected Builder(ImmutableType type, ImmutableProp prop) {
            super(type, prop, null);
        }

        public Builder<K, V> redis(RedisDataSource redisDataSource) {
            this.redisDataSource = redisDataSource;
            return this;
        }

        public Builder<K, V> objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public RedisHashBinder<K, V> build() {
            if (null == redisDataSource) {
                throw new IllegalStateException("RedisDataSource has not been specified");
            }
            return new RedisHashBinder<>(type, prop, tracker, objectMapper, keyPrefixProvider, duration, randomPercent,
                    redisDataSource);
        }
    }
}
