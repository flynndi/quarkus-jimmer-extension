package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.Bean;

import org.babyfish.jimmer.sql.cache.CacheOperator;

import io.quarkus.agroal.DataSource;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

/** Resolves the operator for a CDI-managed SQL client using ArC's bean selection rules. */
public final class QuarkusCacheOperatorProvider {
    private QuarkusCacheOperatorProvider() {
    }

    public static CacheOperator find(ArcContainer container, String dataSourceName) {
        Map<Bean<? extends CacheOperator>, InstanceHandle<CacheOperator>> candidates = new LinkedHashMap<>();
        var handles = new java.util.ArrayList<>(
                container.listAll(CacheOperator.class, new DataSource.DataSourceLiteral(dataSourceName)));
        if (DataSourceUtil.isDefault(dataSourceName)) {
            // Retain the original @DataSource("<default>") override while also supporting ordinary @Default beans.
            handles.addAll(container.listAll(CacheOperator.class, Default.Literal.INSTANCE));
        }
        for (var handle : handles) {
            candidates.put(handle.getBean(), handle);
        }
        // Resolve the combined qualifier sets through ArC, including DefaultBean and alternative priorities.
        // Obtain only the selected handle so unselected operators remain uninitialized.
        var selected = container.beanManager().resolve(candidates.keySet());
        return selected != null ? candidates.get(selected).get() : null;
    }
}
