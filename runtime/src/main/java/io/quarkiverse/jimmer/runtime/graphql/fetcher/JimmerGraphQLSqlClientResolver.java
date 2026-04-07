package io.quarkiverse.jimmer.runtime.graphql.fetcher;

import java.util.List;

import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

final class JimmerGraphQLSqlClientResolver {

    private JimmerGraphQLSqlClientResolver() {
    }

    static JSqlClientImplementor resolve(ImmutableType immutableType) {
        JSqlClientImplementor sqlClient = resolveJavaClient(immutableType);
        if (sqlClient != null) {
            return sqlClient;
        }
        sqlClient = resolveKotlinClient(immutableType);
        if (sqlClient != null) {
            return sqlClient;
        }
        sqlClient = defaultJavaClient();
        if (sqlClient != null) {
            return sqlClient;
        }
        sqlClient = defaultKotlinClient();
        if (sqlClient != null) {
            return sqlClient;
        }
        throw new IllegalStateException(
                "Cannot resolve a Jimmer sql client for GraphQL type \"" + immutableType.getJavaClass().getName() + "\"");
    }

    @Nullable
    private static JSqlClientImplementor resolveJavaClient(ImmutableType immutableType) {
        JSqlClientImplementor candidate = null;
        List<InstanceHandle<QuarkusJSqlClientContainer>> handles = Arc.container().listAll(QuarkusJSqlClientContainer.class);
        for (InstanceHandle<QuarkusJSqlClientContainer> handle : handles) {
            JSqlClientImplementor sqlClient = toJavaImplementor(handle.get());
            if (sqlClient == null || !supports(sqlClient, immutableType)) {
                continue;
            }
            if (DataSourceUtil.isDefault(handle.get().getDataSourceName())) {
                return sqlClient;
            }
            if (candidate == null) {
                candidate = sqlClient;
            }
        }
        return candidate;
    }

    @Nullable
    private static JSqlClientImplementor resolveKotlinClient(ImmutableType immutableType) {
        JSqlClientImplementor candidate = null;
        List<InstanceHandle<QuarkusKSqlClientContainer>> handles = Arc.container().listAll(QuarkusKSqlClientContainer.class);
        for (InstanceHandle<QuarkusKSqlClientContainer> handle : handles) {
            JSqlClientImplementor sqlClient = toKotlinImplementor(handle.get());
            if (sqlClient == null || !supports(sqlClient, immutableType)) {
                continue;
            }
            if (DataSourceUtil.isDefault(handle.get().getDataSourceName())) {
                return sqlClient;
            }
            if (candidate == null) {
                candidate = sqlClient;
            }
        }
        return candidate;
    }

    @Nullable
    private static JSqlClientImplementor defaultJavaClient() {
        try {
            return (JSqlClientImplementor) Jimmer.getDefaultJSqlClient();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static JSqlClientImplementor defaultKotlinClient() {
        try {
            return Jimmer.getDefaultKSqlClient().getJavaClient();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static JSqlClientImplementor toJavaImplementor(QuarkusJSqlClientContainer container) {
        try {
            return (JSqlClientImplementor) container.getjSqlClient();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Nullable
    private static JSqlClientImplementor toKotlinImplementor(QuarkusKSqlClientContainer container) {
        try {
            return container.getKSqlClient().getJavaClient();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean supports(JSqlClientImplementor sqlClient, ImmutableType immutableType) {
        return sqlClient.getEntityManager().getAllTypes(sqlClient.getMicroServiceName()).contains(immutableType);
    }
}
