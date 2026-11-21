package io.quarkiverse.jimmer.runtime.cfg.support;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.di.TransientResolverProvider;

import io.quarkus.arc.ArcContainer;

public class QuarkusTransientResolverProvider implements TransientResolverProvider {

    private final ArcContainer container;
    private final SqlClientBeanAccess access;

    public QuarkusTransientResolverProvider(ArcContainer container) {
        this(container, SqlClientBeanAccess.UNMANAGED);
    }

    public QuarkusTransientResolverProvider(ArcContainer container, SqlClientBeanAccess access) {
        this.container = container;
        this.access = access;
    }

    @Override
    public TransientResolver<?, ?> get(
            Class<TransientResolver<?, ?>> type,
            JSqlClient sqlClient) throws Exception {
        return access.get(container.instance(type));
    }

    @Override
    public TransientResolver<?, ?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = access.get(container.instance(ref));
        if (!(bean instanceof TransientResolver<?, ?>)) {
            throw new IllegalStateException(
                    "The expected type of quarkus bean named \"ref\" is \"" +
                            TransientResolver.class.getName() +
                            "\", but the actual type is + \"" +
                            bean.getClass().getName() +
                            "\"");
        }
        return (TransientResolver<?, ?>) bean;
    }
}
