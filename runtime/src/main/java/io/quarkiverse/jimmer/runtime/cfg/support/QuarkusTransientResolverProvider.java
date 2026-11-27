package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.Objects;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.di.TransientResolverProvider;

import io.quarkiverse.jimmer.runtime.internal.SqlClientBeans;

public class QuarkusTransientResolverProvider implements TransientResolverProvider {

    private final SqlClientBeans beans;

    public QuarkusTransientResolverProvider(SqlClientBeans beans) {
        this.beans = Objects.requireNonNull(beans, "beans must not be null");
    }

    @Override
    public TransientResolver<?, ?> get(
            Class<TransientResolver<?, ?>> type,
            JSqlClient sqlClient) throws Exception {
        return beans.optionalAny(type);
    }

    @Override
    public TransientResolver<?, ?> get(String ref, JSqlClient sqlClient) throws Exception {
        return beans.named(TransientResolver.class, ref);
    }
}
