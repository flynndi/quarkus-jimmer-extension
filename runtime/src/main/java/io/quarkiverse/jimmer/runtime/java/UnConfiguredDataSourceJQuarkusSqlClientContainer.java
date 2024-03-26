package io.quarkiverse.jimmer.runtime.java;

import org.babyfish.jimmer.sql.JSqlClient;

public class UnConfiguredDataSourceJQuarkusSqlClientContainer extends JQuarkusSqlClientContainer {

    private final String message;
    private final Throwable cause;

    public UnConfiguredDataSourceJQuarkusSqlClientContainer(String dataSourceName, String message, Throwable cause) {
        super(null, dataSourceName);
        this.message = message;
        this.cause = cause;
    }

    @Override
    public JSqlClient getjSqlClient() {
        throw new UnsupportedOperationException(message, cause);
    }
}
