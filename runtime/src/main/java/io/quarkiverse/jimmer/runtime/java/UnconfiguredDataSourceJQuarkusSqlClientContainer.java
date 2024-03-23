package io.quarkiverse.jimmer.runtime.java;

import io.quarkiverse.jimmer.runtime.JQuarkusSqlClient;

public class UnconfiguredDataSourceJQuarkusSqlClientContainer extends JQuarkusSqlClientContainer {

    private final String message;
    private final Throwable cause;

    public UnconfiguredDataSourceJQuarkusSqlClientContainer(String dataSourceName, String message, Throwable cause) {
        super(null, dataSourceName);
        this.message = message;
        this.cause = cause;
    }

    public io.quarkiverse.jimmer.runtime.JQuarkusSqlClient getQuarkusJSqlClient() {
        throw new UnsupportedOperationException(message, cause);
    }
}
