package io.quarkiverse.jimmer.runtime.cache;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

public class RedisCaches {

    public static ValueCommands<String, byte[]> cacheRedisValueCommands(RedisDataSource redisDataSource) {
        return redisDataSource.value(byte[].class);
    }

    public static HashCommands<String, String, byte[]> cacheRedisHashCommands(RedisDataSource redisDataSource) {
        return redisDataSource.hash(byte[].class);
    }
}
