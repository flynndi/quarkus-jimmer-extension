package io.quarkiverse.jimmer.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.babyfish.jimmer.sql.JSqlClient;
import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.runtime.util.QuarkusJSqlClientContainerUtil;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

public class Jimmer {

    private static final ConcurrentMap<String, JSqlClient> jSqlClients = new ConcurrentHashMap<>();

    public static JSqlClient getDefaultJSqlClient() {
        return jSqlClients.computeIfAbsent(DataSourceUtil.DEFAULT_DATASOURCE_NAME, new Function<String, JSqlClient>() {
            @Override
            public JSqlClient apply(String s) {
                return QuarkusJSqlClientContainerUtil.instantiateBeanOrClass(JSqlClient.class, QuarkusJSqlClientContainerUtil
                        .getQuarkusJSqlClientContainerQualifier(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
            }
        });
    }

    public static JSqlClient getJSqlClient(@NotNull String dataSourceName) {
        return jSqlClients.computeIfAbsent(dataSourceName, new Function<String, JSqlClient>() {
            @Override
            public JSqlClient apply(String s) {
                return QuarkusJSqlClientContainerUtil.instantiateBeanOrClass(JSqlClient.class,
                        QuarkusJSqlClientContainerUtil.getQuarkusJSqlClientContainerQualifier(dataSourceName));
            }
        });
    }

    public static JQuarkusSqlClientContainer getJSqlClientContainer(@NotNull String dataSourceName) {
        return QuarkusJSqlClientContainerUtil.getQuarkusJSqlClientContainer(dataSourceName);
    }
}
