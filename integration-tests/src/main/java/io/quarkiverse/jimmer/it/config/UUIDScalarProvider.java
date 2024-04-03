package io.quarkiverse.jimmer.it.config;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.runtime.AbstractScalarProvider;
import org.jetbrains.annotations.NotNull;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class UUIDScalarProvider extends AbstractScalarProvider<UUID, String> {

    @Override
    public UUID toScalar(@NotNull String sqlValue) {
        return UUID.fromString(sqlValue);
    }

    @Override
    public String toSql(@NotNull UUID scalarValue) {
        return scalarValue.toString();
    }
}
