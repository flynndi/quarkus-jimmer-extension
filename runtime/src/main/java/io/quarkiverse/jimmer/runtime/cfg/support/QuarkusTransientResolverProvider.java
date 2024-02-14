package io.quarkiverse.jimmer.runtime.cfg.support;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.TransientResolver;
import org.babyfish.jimmer.sql.di.TransientResolverProvider;

import io.quarkus.arc.ArcContainer;

public class QuarkusTransientResolverProvider implements TransientResolverProvider {

    private final ArcContainer container;

    public QuarkusTransientResolverProvider(ArcContainer container) {
        this.container = container;
    }

    @Override
    public TransientResolver<?, ?> get(
            Class<TransientResolver<?, ?>> type,
            JSqlClient sqlClient) throws Exception {
        return container.instance(type).get();
    }

    @Override
    public TransientResolver<?, ?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = container.instance(ref).get();
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
