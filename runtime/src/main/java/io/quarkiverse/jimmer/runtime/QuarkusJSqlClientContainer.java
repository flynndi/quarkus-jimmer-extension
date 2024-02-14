package io.quarkiverse.jimmer.runtime;

public class QuarkusJSqlClientContainer {

    private final QuarkusJSqlClient quarkusJSqlClient;

    private final String dataSourceName;

    private final String id;

    public QuarkusJSqlClientContainer(QuarkusJSqlClient quarkusJSqlClient, String dataSourceName) {
        this.quarkusJSqlClient = quarkusJSqlClient;
        this.dataSourceName = dataSourceName;
        this.id = dataSourceName.replace("<", "").replace(">", "");
    }

    public QuarkusJSqlClient getQuarkusJSqlClient() {
        return quarkusJSqlClient;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public String getId() {
        return id;
    }
}
