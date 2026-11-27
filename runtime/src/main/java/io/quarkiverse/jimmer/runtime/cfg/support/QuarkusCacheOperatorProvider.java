package io.quarkiverse.jimmer.runtime.cfg.support;

import jakarta.enterprise.inject.Default;

import org.babyfish.jimmer.sql.cache.CacheOperator;

import io.quarkiverse.jimmer.runtime.internal.SqlClientBeans;
import io.quarkus.agroal.DataSource;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

/** Resolves the operator for a CDI-managed SQL client using ArC's bean selection rules. */
public final class QuarkusCacheOperatorProvider {
    private QuarkusCacheOperatorProvider() {
    }

    public static CacheOperator find(SqlClientBeans beans) {
        String dataSourceName = beans.dataSourceName();
        var named = new DataSource.DataSourceLiteral(dataSourceName);
        // Retain the original @DataSource("<default>") override while also supporting ordinary @Default beans.
        return DataSourceUtil.isDefault(dataSourceName)
                ? beans.resolve(CacheOperator.class, named, Default.Literal.INSTANCE)
                : beans.resolve(CacheOperator.class, named);
    }
}
