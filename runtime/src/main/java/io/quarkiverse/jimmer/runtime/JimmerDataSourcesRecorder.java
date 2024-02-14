package io.quarkiverse.jimmer.runtime;

import java.util.Locale;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;

import io.quarkiverse.jimmer.runtime.util.QuarkusJSqlClientContainerUtil;
import io.quarkus.agroal.runtime.DataSources;
import io.quarkus.agroal.runtime.UnconfiguredDataSource;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class JimmerDataSourcesRecorder {

    public Function<SyntheticCreationalContext<QuarkusJSqlClientContainer>, QuarkusJSqlClientContainer> sqlClientContainerFunction(
            String dataSourceName, Dialect dialect) {
        return context -> {
            DataSource dataSource;
            try {
                dataSource = context.getInjectedReference(DataSources.class).getDataSource(dataSourceName);
                if (dataSource instanceof UnconfiguredDataSource) {
                    throw new ConfigurationException(String.format(Locale.ROOT,
                            "Datasource '%s' is not configured."
                                    + " To solve this, configure datasource '%s'."
                                    + " Refer to https://quarkus.io/guides/datasource for guidance.",
                            dataSourceName, dataSourceName));
                }
            } catch (ConfigurationException e) {
                return new UnconfiguredDataSourceQuarkusJSqlClientContainer(dataSourceName, String.format(Locale.ROOT,
                        "Unable to find datasource '%s' for Jimmer: %s",
                        dataSourceName, e.getMessage()), e);
            }
            QuarkusJSqlClientProducer producer = context.getInjectedReference(QuarkusJSqlClientProducer.class);
            return producer.createQuarkusJSqlClient(dataSource, dataSourceName, dialect);
        };
    }

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> quarkusJSqlClientFunction(String dataSourceName) {
        return context -> {
            QuarkusJSqlClientContainer quarkusJSqlClientContainer = context.getInjectedReference(
                    QuarkusJSqlClientContainer.class,
                    QuarkusJSqlClientContainerUtil.getQuarkusJSqlClientContainerQualifier(dataSourceName));
            return quarkusJSqlClientContainer.getQuarkusJSqlClient();
        };
    }
}
