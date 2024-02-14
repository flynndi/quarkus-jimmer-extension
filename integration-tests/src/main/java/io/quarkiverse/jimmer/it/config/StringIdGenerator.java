package io.quarkiverse.jimmer.it.config;

import java.util.UUID;

import org.babyfish.jimmer.sql.meta.UserIdGenerator;

public class StringIdGenerator implements UserIdGenerator<String> {

    @Override
    public String generate(Class<?> entityType) {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
