package io.quarkiverse.jimmer.runtime;

import java.util.Locale;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.java.JQuarkusSqlClientContainer;
import io.quarkiverse.jimmer.runtime.java.UnConfiguredDataSourceJQuarkusSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.KQuarkusSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.UnConfiguredDataSourceKQuarkusSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.agroal.runtime.DataSources;
import io.quarkus.agroal.runtime.UnconfiguredDataSource;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class JimmerDataSourcesRecorder {

    public Function<SyntheticCreationalContext<JQuarkusSqlClientContainer>, JQuarkusSqlClientContainer> jSqlClientContainerFunction(
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
                return new UnConfiguredDataSourceJQuarkusSqlClientContainer(dataSourceName, String.format(Locale.ROOT,
                        "Unable to find datasource '%s' for Jimmer: %s",
                        dataSourceName, e.getMessage()), e);
            }
            QuarkusSqlClientProducer producer = context.getInjectedReference(QuarkusSqlClientProducer.class);
            return producer.createJQuarkusSqlClient(dataSource, dataSourceName, dialect);
        };
    }

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> quarkusJSqlClientFunction(String dataSourceName) {
        return context -> {
            JQuarkusSqlClientContainer JQuarkusSqlClientContainer = context.getInjectedReference(
                    JQuarkusSqlClientContainer.class,
                    QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
            return JQuarkusSqlClientContainer.getjSqlClient();
        };
    }

    public Function<SyntheticCreationalContext<KQuarkusSqlClientContainer>, KQuarkusSqlClientContainer> kSqlClientContainerFunction(
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
                return new UnConfiguredDataSourceKQuarkusSqlClientContainer(dataSourceName, String.format(Locale.ROOT,
                        "Unable to find datasource '%s' for Jimmer: %s",
                        dataSourceName, e.getMessage()), e);
            }
            QuarkusSqlClientProducer producer = context.getInjectedReference(QuarkusSqlClientProducer.class);
            return producer.createKQuarkusSqlClient(dataSource, dataSourceName, dialect);
        };
    }

    public Function<SyntheticCreationalContext<KSqlClient>, KSqlClient> quarkusKSqlClientFunction(String dataSourceName) {
        return context -> {
            KQuarkusSqlClientContainer KQuarkusSqlClientContainer = context.getInjectedReference(
                    KQuarkusSqlClientContainer.class,
                    QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
            return KQuarkusSqlClientContainer.getKSqlClient();
        };
    }
}
