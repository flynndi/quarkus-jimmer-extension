package io.quarkiverse.jimmer.it.config;

import java.util.UUID;

import org.babyfish.jimmer.sql.meta.UserIdGenerator;

public class UUIDGenerator implements UserIdGenerator<UUID> {

    @Override
    public UUID generate(Class<?> entityType) {
        return UUID.randomUUID();
    }
}
