package io.quarkiverse.jimmer.runtime.cache;

import java.util.List;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.cache.Cache;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.cache.FilterState;
import org.babyfish.jimmer.sql.cache.FilterStateAware;
import org.babyfish.jimmer.sql.cache.FilterStateAwareCacheFactory;

import io.quarkus.arc.ClientProxy;

/** Exposes filter-state support hidden by an interface-typed CDI producer while retaining its contextual reference. */
public final class QuarkusCacheFactory implements FilterStateAwareCacheFactory {

    private final CacheFactory delegate;

    private QuarkusCacheFactory(CacheFactory delegate) {
        this.delegate = delegate;
    }

    public static CacheFactory adapt(CacheFactory factory) {
        if (factory instanceof ClientProxy && !(factory instanceof FilterStateAware)) {
            return new QuarkusCacheFactory(factory);
        }
        return factory;
    }

    @Override
    public void setFilterState(FilterState filterState) {
        // This method is absent from the proxy's declared type; resolve its current contextual instance on every call.
        // Delay resolution until Jimmer uses this factory: a later customizer may replace it entirely.
        if (ClientProxy.unwrap(delegate) instanceof FilterStateAware aware) {
            aware.setFilterState(filterState);
        }
    }

    @Override
    public Cache<?, ?> createObjectCache(ImmutableType type) {
        return delegate.createObjectCache(type);
    }

    @Override
    public Cache<?, ?> createAssociatedIdCache(ImmutableProp prop) {
        return delegate.createAssociatedIdCache(prop);
    }

    @Override
    public Cache<?, List<?>> createAssociatedIdListCache(ImmutableProp prop) {
        return delegate.createAssociatedIdListCache(prop);
    }

    @Override
    public Cache<?, ?> createResolverCache(ImmutableProp prop) {
        return delegate.createResolverCache(prop);
    }
}
