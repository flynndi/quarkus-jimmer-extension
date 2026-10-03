package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.enterprise.inject.AmbiguousResolutionException;
import jakarta.enterprise.inject.Default;

import org.babyfish.jimmer.sql.cache.CacheOperator;

import io.quarkiverse.jimmer.runtime.JimmerTransactionCacheOperatorRecorder;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

/** Keeps the extension's datasource-scoped default operator exclusive to its managed SQL client. */
public final class QuarkusCacheOperatorProvider {
    private QuarkusCacheOperatorProvider() {
    }

    public static CacheOperator findUserOperator(ArcContainer container, String dataSourceName) {
        return find(container, dataSourceName, false);
    }

    public static CacheOperator findManagedOperator(ArcContainer container, String dataSourceName) {
        return find(container, dataSourceName, true);
    }

    private static CacheOperator find(ArcContainer container, String dataSourceName, boolean includeExtensionDefault) {
        Map<String, InstanceHandle<CacheOperator>> users = new LinkedHashMap<>();
        InstanceHandle<CacheOperator> extensionDefault = null;
        var handles = new java.util.ArrayList<>(
                container.listAll(CacheOperator.class, new DataSource.DataSourceLiteral(dataSourceName)));
        if (DataSourceUtil.isDefault(dataSourceName)) {
            // Retain the original @DataSource("<default>") override while also supporting ordinary @Default beans.
            handles.addAll(container.listAll(CacheOperator.class, Default.Literal.INSTANCE));
        }
        for (var handle : handles) {
            var bean = handle.getBean();
            if (bean.getKind() == InjectableBean.Kind.SYNTHETIC
                    && bean.getImplementationClass() == JimmerTransactionCacheOperatorRecorder.LazyTransactionCacheOperator.class) {
                extensionDefault = handle;
            } else {
                users.put(bean.getIdentifier(), handle);
            }
        }
        if (users.size() > 1) {
            throw new AmbiguousResolutionException("Multiple CacheOperator beans match datasource '" + dataSourceName + "'");
        }
        if (!users.isEmpty()) {
            return users.values().iterator().next().get();
        }
        return includeExtensionDefault && extensionDefault != null ? extensionDefault.get() : null;
    }
}
