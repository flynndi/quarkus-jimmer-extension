package io.quarkiverse.jimmer.runtime;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

public class Jimmer {

    public static JSqlClient getDefaultJSqlClient() {
        return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(JSqlClient.class, QuarkusSqlClientContainerUtil
                .getQuarkusSqlClientContainerQualifier(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
    }

    public static JSqlClient getJSqlClient(@NotNull String dataSourceName) {
        return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(JSqlClient.class,
                QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
    }

    public static QuarkusJSqlClientContainer getJSqlClientContainer(@NotNull String dataSourceName) {
        return QuarkusSqlClientContainerUtil.getQuarkusJSqlClientContainer(dataSourceName);
    }

    public static KSqlClient getDefaultKSqlClient() {
        return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(KSqlClient.class, QuarkusSqlClientContainerUtil
                .getQuarkusSqlClientContainerQualifier(DataSourceUtil.DEFAULT_DATASOURCE_NAME));
    }

    public static KSqlClient getKSqlClient(@NotNull String dataSourceName) {
        return QuarkusSqlClientContainerUtil.instantiateBeanOrClass(KSqlClient.class,
                QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
    }

    public static QuarkusKSqlClientContainer getKSqlClientContainer(@NotNull String dataSourceName) {
        return QuarkusSqlClientContainerUtil.getQuarkusKSqlClientContainer(dataSourceName);
    }
}
