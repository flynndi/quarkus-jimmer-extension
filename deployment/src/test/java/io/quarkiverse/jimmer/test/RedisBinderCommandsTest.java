package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.babyfish.jimmer.meta.ImmutableType;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cache.RedisHashBinder;
import io.quarkiverse.jimmer.runtime.cache.RedisValueBinder;
import io.quarkiverse.jimmer.test.model.CdiBook;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;

class RedisBinderCommandsTest {

    @Test
    void hashWritesExpireEachKeyOnceAndKeepParameterVariantsSeparate() {
        RedisCommands redis = new RedisCommands();
        RedisHashBinder<Long, String> binder = hashBinder(redis);
        Map<Long, String> values = new LinkedHashMap<>();
        values.put(1L, "first");
        values.put(2L, "second");
        SortedMap<String, Object> firstParameters = new TreeMap<>(Map.of("tenant", "first"));
        SortedMap<String, Object> secondParameters = new TreeMap<>(Map.of("tenant", "second"));

        binder.setAll(values, firstParameters);

        assertEquals(List.of("hset:CdiBook.name-1", "pexpire:CdiBook.name-1", "hset:CdiBook.name-2",
                "pexpire:CdiBook.name-2"), redis.calls);
        assertExpireMillis(redis, "CdiBook.name-1", "CdiBook.name-2");
        assertEquals(values, binder.getAll(values.keySet(), firstParameters));
        assertTrue(binder.getAll(values.keySet(), secondParameters).isEmpty());

        binder.setAll(Map.of(1L, "other variant"), secondParameters);
        assertEquals("first", binder.getAll(List.of(1L), firstParameters).get(1L));
        assertEquals("other variant", binder.getAll(List.of(1L), secondParameters).get(1L));

        binder.deleteAll(List.of(1L, 2L), "redis");

        assertEquals(List.of("CdiBook.name-1", "CdiBook.name-2"), redis.deletedKeys);
        assertEquals(List.of(List.of("CdiBook.name-1"), List.of("CdiBook.name-2")), redis.deleteCommands);
        assertTrue(binder.getAll(values.keySet(), firstParameters).isEmpty());
        assertTrue(binder.getAll(List.of(1L), secondParameters).isEmpty());
    }

    @Test
    void valueWritesExpireKeysWithoutReadingTheirPayloadAndDeleteKeysIndividually() {
        RedisCommands redis = new RedisCommands();
        RedisValueBinder<Long, String> binder = valueBinder(redis);
        Map<Long, String> values = new LinkedHashMap<>();
        values.put(1L, "first");
        values.put(2L, "second");

        binder.setAll(values);

        assertEquals(List.of("mset", "pexpire:CdiBook.name-1", "pexpire:CdiBook.name-2"), redis.calls);
        assertExpireMillis(redis, "CdiBook.name-1", "CdiBook.name-2");
        assertEquals(values, binder.getAll(values.keySet()));

        binder.deleteAll(List.of(1L, 2L), "redis");

        assertEquals(List.of("CdiBook.name-1", "CdiBook.name-2"), redis.deletedKeys);
        assertEquals(List.of(List.of("CdiBook.name-1"), List.of("CdiBook.name-2")), redis.deleteCommands);
        assertTrue(binder.getAll(values.keySet()).isEmpty());
    }

    @Test
    void emptyBatchesDoNotSendRedisCommands() {
        RedisCommands redis = new RedisCommands();
        RedisHashBinder<Long, String> hashBinder = hashBinder(redis);
        RedisValueBinder<Long, String> valueBinder = valueBinder(redis);

        hashBinder.setAll(Map.of());
        valueBinder.setAll(Map.of());
        assertTrue(hashBinder.getAll(List.of()).isEmpty());
        assertTrue(valueBinder.getAll(List.of()).isEmpty());
        hashBinder.deleteAll(List.of(), "redis");
        valueBinder.deleteAll(List.of(), "redis");

        assertTrue(redis.calls.isEmpty());
    }

    private static RedisHashBinder<Long, String> hashBinder(RedisCommands redis) {
        return RedisHashBinder.<Long, String> forProp(ImmutableType.get(CdiBook.class).getProp("name"))
                .duration(Duration.ofSeconds(2)).randomPercent(10).redis(redis.dataSource).build();
    }

    private static RedisValueBinder<Long, String> valueBinder(RedisCommands redis) {
        return RedisValueBinder.<Long, String> forProp(ImmutableType.get(CdiBook.class).getProp("name"))
                .duration(Duration.ofSeconds(2)).randomPercent(10).redis(redis.dataSource).build();
    }

    private static void assertExpireMillis(RedisCommands redis, String... keys) {
        assertEquals(Arrays.asList(keys), new ArrayList<>(redis.expirations.keySet()));
        redis.expirations.values().forEach(millis -> assertTrue(millis >= 1800 && millis < 2200,
                "The configured duration must be passed to PEXPIRE in milliseconds: " + millis));
    }

    private static final class RedisCommands {

        final List<String> calls = new ArrayList<>();
        final Map<String, Long> expirations = new LinkedHashMap<>();
        final Map<String, Map<String, byte[]>> hashes = new LinkedHashMap<>();
        final Map<String, byte[]> values = new LinkedHashMap<>();
        final List<String> deletedKeys = new ArrayList<>();
        final List<List<String>> deleteCommands = new ArrayList<>();
        final RedisDataSource dataSource;

        RedisCommands() {
            HashCommands<String, String, byte[]> hashCommands = proxy(HashCommands.class, (proxy, method, args) -> {
                String key = (String) args[0];
                if (method.getName().equals("hset")) {
                    calls.add("hset:" + key);
                    hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>()).put((String) args[1], (byte[]) args[2]);
                    return true;
                }
                if (method.getName().equals("hget")) {
                    calls.add("hget:" + key);
                    return hashes.getOrDefault(key, Map.of()).get(args[1]);
                }
                throw new AssertionError("Unexpected hash command: " + method);
            });
            KeyCommands<String> keyCommands = proxy(KeyCommands.class, (proxy, method, args) -> {
                if (method.getName().equals("pexpire")) {
                    String key = (String) args[0];
                    assertTrue(hashes.containsKey(key) || values.containsKey(key), "Expiry must follow the write");
                    calls.add("pexpire:" + key);
                    expirations.put(key, (Long) args[1]);
                    return true;
                }
                if (method.getName().equals("del")) {
                    calls.add("del");
                    String[] keys = (String[]) args[0];
                    deleteCommands.add(List.copyOf(Arrays.asList(keys)));
                    deletedKeys.addAll(Arrays.asList(keys));
                    for (String key : keys) {
                        hashes.remove(key);
                        values.remove(key);
                        expirations.remove(key);
                    }
                    return keys.length;
                }
                throw new AssertionError("Unexpected key command: " + method);
            });
            ValueCommands<String, byte[]> valueCommands = proxy(ValueCommands.class, (proxy, method, args) -> {
                if (method.getName().equals("mset")) {
                    calls.add("mset");
                    @SuppressWarnings("unchecked")
                    Map<String, byte[]> input = (Map<String, byte[]>) args[0];
                    values.putAll(input);
                    return null;
                }
                if (method.getName().equals("mget")) {
                    calls.add("mget");
                    Map<String, byte[]> output = new LinkedHashMap<>();
                    for (String key : (String[]) args[0]) {
                        output.put(key, values.get(key));
                    }
                    return output;
                }
                throw new AssertionError("Unexpected value command: " + method);
            });
            dataSource = proxy(RedisDataSource.class, (proxy, method, args) -> switch (method.getName()) {
                case "hash" -> hashCommands;
                case "key" -> keyCommands;
                case "value" -> valueCommands;
                default -> throw new AssertionError("Unexpected datasource operation: " + method);
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
        }
    }
}
