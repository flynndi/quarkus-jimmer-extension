package io.quarkiverse.jimmer.runtime;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jetbrains.annotations.NotNull;

import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.Arc;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

public class Jimmer {

    public static JSqlClient getDefaultJSqlClient() {
        return getJSqlClient(DataSourceUtil.DEFAULT_DATASOURCE_NAME);
    }

    public static JSqlClient getJSqlClient(@NotNull String dataSourceName) {
        return Arc.container().select(JSqlClient.class, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
    }

    public static KSqlClient getDefaultKSqlClient() {
        return getKSqlClient(DataSourceUtil.DEFAULT_DATASOURCE_NAME);
    }

    public static KSqlClient getKSqlClient(@NotNull String dataSourceName) {
        return Arc.container().select(KSqlClient.class, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
    }
}
