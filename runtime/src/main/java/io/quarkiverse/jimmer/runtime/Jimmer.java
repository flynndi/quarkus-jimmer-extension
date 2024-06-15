package io.quarkiverse.jimmer.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

public class Jimmer {

    /**
     * Java SqlClients ConcurrentMap
     */
    private static final ConcurrentMap<String, JSqlClient> jSqlClients = new ConcurrentHashMap<>();

    /**
     * Kotlin SqlClients ConcurrentMap
     */
    private static final ConcurrentMap<String, KSqlClient> kSqlClients = new ConcurrentHashMap<>();

    public static JSqlClient getDefaultJSqlClient() {
        return jSqlClients.computeIfAbsent(DataSourceUtil.DEFAULT_DATASOURCE_NAME, new Function<String, JSqlClient>() {
            @Override
            public JSqlClient apply(String s) {
                return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(JSqlClient.class, QuarkusSqlClientContainerUtil
                        .getQuarkusSqlClientContainerQualifier(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
            }
        });
    }

    public static JSqlClient getJSqlClient(@NotNull String dataSourceName) {
        return jSqlClients.computeIfAbsent(dataSourceName, new Function<String, JSqlClient>() {
            @Override
            public JSqlClient apply(String s) {
                return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(JSqlClient.class,
                        QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
            }
        });
    }

    public static QuarkusJSqlClientContainer getJSqlClientContainer(@NotNull String dataSourceName) {
        return QuarkusSqlClientContainerUtil.getQuarkusJSqlClientContainer(dataSourceName);
    }

    public static KSqlClient getDefaultKSqlClient() {
        return kSqlClients.computeIfAbsent(DataSourceUtil.DEFAULT_DATASOURCE_NAME, new Function<String, KSqlClient>() {
            @Override
            public KSqlClient apply(String s) {
                return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(KSqlClient.class, QuarkusSqlClientContainerUtil
                        .getQuarkusSqlClientContainerQualifier(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
            }
        });
    }

    public static KSqlClient getKSqlClient(@NotNull String dataSourceName) {
        return kSqlClients.computeIfAbsent(dataSourceName, new Function<String, KSqlClient>() {
            @Override
            public KSqlClient apply(String s) {
                return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(KSqlClient.class,
                        QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
            }
        });
    }

    public static QuarkusKSqlClientContainer getKSqlClientContainer(@NotNull String dataSourceName) {
        return QuarkusSqlClientContainerUtil.getQuarkusKSqlClientContainer(dataSourceName);
    }
}
