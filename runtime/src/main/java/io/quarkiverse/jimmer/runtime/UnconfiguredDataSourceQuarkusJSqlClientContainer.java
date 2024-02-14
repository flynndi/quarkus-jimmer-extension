package io.quarkiverse.jimmer.runtime;

public class UnconfiguredDataSourceQuarkusJSqlClientContainer extends QuarkusJSqlClientContainer {

    private final String message;
    private final Throwable cause;

    public UnconfiguredDataSourceQuarkusJSqlClientContainer(String dataSourceName, String message, Throwable cause) {
        super(null, dataSourceName);
        this.message = message;
        this.cause = cause;
    }

    public QuarkusJSqlClient getQuarkusJSqlClient() {
        throw new UnsupportedOperationException(message, cause);
    }
}
