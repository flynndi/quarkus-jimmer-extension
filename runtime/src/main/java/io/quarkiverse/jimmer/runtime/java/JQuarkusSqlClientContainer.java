package io.quarkiverse.jimmer.runtime.java;

import io.quarkiverse.jimmer.runtime.JQuarkusSqlClient;

public class JQuarkusSqlClientContainer {

    private final JQuarkusSqlClient JQuarkusSqlClient;

    private final String dataSourceName;

    private final String id;

    public JQuarkusSqlClientContainer(JQuarkusSqlClient JQuarkusSqlClient, String dataSourceName) {
        this.JQuarkusSqlClient = JQuarkusSqlClient;
        this.dataSourceName = dataSourceName;
        this.id = dataSourceName.replace("<", "").replace(">", "");
    }

    public JQuarkusSqlClient getQuarkusJSqlClient() {
        return JQuarkusSqlClient;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public String getId() {
        return id;
    }
}
