package io.quarkiverse.jimmer.runtime;

public class UnconfiguredDataSourceJQuarkusSqlClientContainer extends JQuarkusSqlClientContainer {

    private final String message;
    private final Throwable cause;

    public UnconfiguredDataSourceJQuarkusSqlClientContainer(String dataSourceName, String message, Throwable cause) {
        super(null, dataSourceName);
        this.message = message;
        this.cause = cause;
    }

    public JQuarkusSqlClient getQuarkusJSqlClient() {
        throw new UnsupportedOperationException(message, cause);
    }
}
