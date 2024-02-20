package io.quarkiverse.jimmer.runtime.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.spi.AbstractRemoteHashBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.value.GetExArgs;
import io.quarkus.redis.datasource.value.ValueCommands;

public class RedisHashBinder<K, V> extends AbstractRemoteHashBinder<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisHashBinder.class);

    private final HashCommands<String, String, byte[]> hashCommands;

    private final ValueCommands<String, byte[]> valueCommands;

    public RedisHashBinder(
            HashCommands<String, String, byte[]> hashCommands,
            ValueCommands<String, byte[]> valueCommands,
            ObjectMapper objectMapper,
            ImmutableType type,
            Duration duration) {
        super(objectMapper, type, null, duration, 30);
        this.hashCommands = hashCommands;
        this.valueCommands = valueCommands;
    }

    public RedisHashBinder(
            RedisDataSource redisDataSource,
            ObjectMapper objectMapper,
            ImmutableType type,
            Duration duration) {
        super(objectMapper, type, null, duration, 30);
        this.hashCommands = RedisCaches.cacheRedisHashCommands(redisDataSource);
        this.valueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);
    }

    public RedisHashBinder(
            HashCommands<String, String, byte[]> hashCommands,
            ValueCommands<String, byte[]> valueCommands,
            ObjectMapper objectMapper,
            ImmutableProp prop,
            Duration duration) {
        super(objectMapper, null, prop, duration, 30);
        this.hashCommands = hashCommands;
        this.valueCommands = valueCommands;
    }

    public RedisHashBinder(
            RedisDataSource redisDataSource,
            ObjectMapper objectMapper,
            ImmutableProp prop,
            Duration duration) {
        super(objectMapper, null, prop, duration, 30);
        this.hashCommands = RedisCaches.cacheRedisHashCommands(redisDataSource);
        this.valueCommands = RedisCaches.cacheRedisValueCommands(redisDataSource);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected List<byte[]> read(Collection<String> keys, String hashKey) {
        if (keys.isEmpty()) {
            return null;
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
            for (String key : map.keySet()) {
                valueCommands.getex(key, new GetExArgs().px(nextExpireMillis()));
            }
        }
    }

    @Override
    protected void delete(Collection<String> keys) {
        LOGGER.info("Delete data from redis: {}", keys);
        for (String key : keys) {
            hashCommands.hdel(key);
        }
    }

    @Override
    protected String reason() {
        return "redis";
    }
}
