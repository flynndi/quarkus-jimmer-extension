package io.quarkiverse.jimmer.runtime.java;

import org.babyfish.jimmer.sql.JSqlClient;

public class UnConfiguredDataSourceQuarkusJSqlClientContainer extends QuarkusJSqlClientContainer {

    private final String message;
    private final Throwable cause;

    public UnConfiguredDataSourceQuarkusJSqlClientContainer(String dataSourceName, String message, Throwable cause) {
        super(null, dataSourceName);
        this.message = message;
        this.cause = cause;
    }

    @Override
    public JSqlClient getjSqlClient() {
        throw new UnsupportedOperationException(message, cause);
    }
}
