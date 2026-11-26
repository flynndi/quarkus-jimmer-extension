package io.quarkiverse.jimmer.it.resource;

import static org.hamcrest.Matchers.emptyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.CacheTracker;
import org.babyfish.jimmer.sql.cache.RemoteKeyPrefixProvider;
import org.babyfish.jimmer.sql.cache.redisson.RedissonCacheTracker;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;

import io.quarkiverse.jimmer.it.entity.UserRole;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import tools.jackson.databind.json.JsonMapper;

@QuarkusIntegrationTest
public class JimmerHttpIT extends JimmerHttpTestCase {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void cachesMissingRolesOnlyWhenTheSoftLockIsAvailable(boolean lockHeldByAnotherClient) throws IOException {
        UUID id = UUID.randomUUID();
        String prefix = RemoteKeyPrefixProvider.DEFAULT.typeKeyPrefix(ImmutableType.get(UserRole.class));
        var redis = Redisson.create(Config.fromYAML(JimmerHttpIT.class.getResource("/redisson-test.yaml")));
        var bucket = redis.getBucket(prefix + id, StringCodec.INSTANCE);
        // RedissonCacheLocker in Jimmer 0.12.5 uses this lock name for a single missed key.
        var lock = redis.getLock("$lock$" + prefix + "-" + id);
        try {
            assertFalse(bucket.isExists(), "The first read must miss the shared cache");
            if (lockHeldByAnotherClient) {
                lock.lock(30, TimeUnit.SECONDS);
            }

            request().queryParam("id", id).get("/userRoleResources/userRoleFindById")
                    .then().statusCode(200).body(emptyString());
            assertEquals(lockHeldByAnotherClient, lock.isLocked(), "The read must finish while a competing lock is held");
            assertEquals(!lockHeldByAnotherClient, bucket.isExists(), "Only the lock holder may cache the missing role");
        } finally {
            try {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                bucket.delete();
            } finally {
                redis.shutdown();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidationIds")
    void invalidatesLocalCacheOnBroadcastFromAnotherProcess(Set<UUID> ids) throws IOException, InterruptedException {
        var type = ImmutableType.get(UserRole.class);
        String keyPrefix = RemoteKeyPrefixProvider.DEFAULT.typeKeyPrefix(type);
        var redis = Redisson.create(Config.fromYAML(JimmerHttpIT.class.getResource("/redisson-test.yaml")));
        List<RBucket<String>> buckets = new ArrayList<>();
        try {
            var tracker = new RedissonCacheTracker(redis);
            var mapper = JsonMapper.builder().build();
            for (UUID id : ids) {
                RBucket<String> bucket = redis.getBucket(keyPrefix + id, StringCodec.INSTANCE);
                buckets.add(bucket);
                var entry = mapper.createObjectNode()
                        .put("id", id.toString()).put("userId", "user").put("roleId", "original")
                        .put("deleteFlag", false).putNull("authUser");
                bucket.set(entry.toString(), Duration.ofMinutes(2));
                assertEquals("original", findRoleId(id));

                bucket.setAndKeepTTL(entry.put("roleId", "updated").toString());
                assertEquals("original", findRoleId(id), "The application must still serve its local cache entry for " + id);
            }

            tracker.publisher().invalidate(new CacheTracker.InvalidateEvent(type, ids));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            for (UUID id : ids) {
                String actual = findRoleId(id);
                while (!"updated".equals(actual) && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                    actual = findRoleId(id);
                }
                assertEquals("updated", actual,
                        "The remote invalidation must evict the application's local cache entry for " + id);
            }
        } finally {
            try {
                buckets.forEach(RBucket::delete);
            } finally {
                redis.shutdown();
            }
        }
    }

    private static List<Set<UUID>> invalidationIds() {
        // Cache.delete wraps one key, TransactionCacheOperator merges batches, and deleteAll preserves caller sets.
        return List.of(Collections.singleton(UUID.randomUUID()),
                new LinkedHashSet<>(List.of(UUID.randomUUID(), UUID.randomUUID())),
                Set.of(UUID.randomUUID()),
                Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
    }

    private static String findRoleId(UUID id) {
        return request().queryParam("id", id).get("/userRoleResources/userRoleFindById")
                .then().statusCode(200).extract().path("roleId");
    }
}
