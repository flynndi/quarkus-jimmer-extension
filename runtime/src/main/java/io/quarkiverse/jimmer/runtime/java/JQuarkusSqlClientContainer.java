package io.quarkiverse.jimmer.runtime.java;

import org.babyfish.jimmer.sql.JSqlClient;

public class JQuarkusSqlClientContainer {

    private final JSqlClient jSqlClient;

    private final String dataSourceName;

    private final String id;

    public JQuarkusSqlClientContainer(JSqlClient jSqlClient, String dataSourceName) {
        this.jSqlClient = jSqlClient;
        this.dataSourceName = dataSourceName;
        this.id = dataSourceName.replace("<", "").replace(">", "");
    }

    public JSqlClient getjSqlClient() {
        return jSqlClient;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public String getId() {
        return id;
    }
}
