package io.quarkiverse.jimmer.runtime.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.GetExArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.spi.AbstractRemoteValueBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RedisValueBinder<K, V> extends AbstractRemoteValueBinder<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisValueBinder.class);

    private final ValueCommands<String, byte[]> operations;

    public RedisValueBinder(
            ValueCommands<String, byte[]> operations,
            ObjectMapper objectMapper,
            ImmutableType type,
            Duration duration
    ) {
        super(objectMapper,type, null, duration, 30);
        this.operations = operations;
    }

    public RedisValueBinder(
            RedisDataSource redisDataSource,
            ObjectMapper objectMapper,
            ImmutableType type,
            Duration duration
    ) {
        super(objectMapper,type, null, duration, 30);
        this.operations = RedisCaches.cacheRedisValueCommands(redisDataSource);
    }

    public RedisValueBinder(
            ValueCommands<String, byte[]> operations,
            ObjectMapper objectMapper,
            ImmutableProp prop,
            Duration duration
    ) {
        super(objectMapper,null, prop, duration, 30);
        this.operations = operations;
    }

    public RedisValueBinder(
            RedisDataSource redisDataSource,
            ObjectMapper objectMapper,
            ImmutableProp prop,
            Duration duration
    ) {
        super(objectMapper,null, prop, duration, 30);
        this.operations = RedisCaches.cacheRedisValueCommands(redisDataSource);
    }

    @Override
    protected List<byte[]> read(Collection<String> keys) {
        return this.multiGet(keys, operations);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void write(Map<String, byte[]> map) {
        operations.mset(map);
        for (String key : map.keySet()) {
            operations.getex(key, new GetExArgs().px(nextExpireMillis()));
        }
    }

    @Override
    protected void delete(Collection<String> keys) {
        LOGGER.info("Delete data from redis: {}", keys);
        for (String key : keys) {
            operations.getdel(key);
        }
    }

    @Override
    protected String reason() {
        return "redis";
    }



    private List<byte[]> multiGet(Collection<String> keys, ValueCommands<String, byte[]> operations) {
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        String[] array = keys.toArray(keys.toArray(new String[0]));
        Map<String, byte[]> mGet = operations.mget(array);
        return mGet.values().stream().toList();
    }
}
