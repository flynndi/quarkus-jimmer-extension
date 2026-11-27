package io.quarkiverse.jimmer.runtime.cfg.support;

import jakarta.enterprise.inject.Any;

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
        if (container.select(type, Any.Literal.INSTANCE).isUnsatisfied()) {
            return null;
        }
        return access.lookup(container, root -> root.select(type, Any.Literal.INSTANCE).get());
    }

    @Override
    public TransientResolver<?, ?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = access.get(container.instance(ref));
        if (bean instanceof TransientResolver<?, ?> resolver) {
            return resolver;
        }
        throw new IllegalStateException(bean == null
                ? "Cannot resolve a Quarkus bean named \"" + ref + "\""
                : "Quarkus bean named \"" + ref + "\" has type \"" + bean.getClass().getName()
                        + "\", expected \"" + TransientResolver.class.getName() + "\"");
    }
}
