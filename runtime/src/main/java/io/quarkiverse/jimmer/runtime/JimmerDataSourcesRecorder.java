package io.quarkiverse.jimmer.runtime;

import java.util.Locale;
import java.util.function.Function;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.java.UnConfiguredDataSourceQuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.UnConfiguredDataSourceQuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.agroal.runtime.DataSources;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.runtime.configuration.ConfigurationException;

@Recorder
public class JimmerDataSourcesRecorder {

    public Function<SyntheticCreationalContext<QuarkusJSqlClientContainer>, QuarkusJSqlClientContainer> jSqlClientContainerFunction(
            String dataSourceName) {
        return context -> {
            DataSource dataSource;
            try {
                dataSource = context.getInjectedReference(DataSources.class).getDataSource(dataSourceName);
            } catch (ConfigurationException e) {
                return new UnConfiguredDataSourceQuarkusJSqlClientContainer(dataSourceName, String.format(Locale.ROOT,
                        "Unable to find datasource '%s' for Jimmer: %s",
                        dataSourceName, e.getMessage()), e);
            }
            QuarkusSqlClientProducer producer = context.getInjectedReference(QuarkusSqlClientProducer.class);
            return producer.createQuarkusJSqlClientContainer(dataSource, dataSourceName);
        };
    }

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> quarkusJSqlClientFunction(String dataSourceName) {
        return context -> {
            QuarkusJSqlClientContainer QuarkusJSqlClientContainer = context.getInjectedReference(
                    QuarkusJSqlClientContainer.class,
                    QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
            return QuarkusJSqlClientContainer.getjSqlClient();
        };
    }

    public Function<SyntheticCreationalContext<QuarkusKSqlClientContainer>, QuarkusKSqlClientContainer> kSqlClientContainerFunction(
            String dataSourceName) {
        return context -> {
            DataSource dataSource;
            try {
                dataSource = context.getInjectedReference(DataSources.class).getDataSource(dataSourceName);
            } catch (ConfigurationException e) {
                return new UnConfiguredDataSourceQuarkusKSqlClientContainer(dataSourceName, String.format(Locale.ROOT,
                        "Unable to find datasource '%s' for Jimmer: %s",
                        dataSourceName, e.getMessage()), e);
            }
            QuarkusSqlClientProducer producer = context.getInjectedReference(QuarkusSqlClientProducer.class);
            return producer.createQuarkusKSqlClientContainer(dataSource, dataSourceName);
        };
    }

    public Function<SyntheticCreationalContext<KSqlClient>, KSqlClient> quarkusKSqlClientFunction(String dataSourceName) {
        return context -> {
            QuarkusKSqlClientContainer QuarkusKSqlClientContainer = context.getInjectedReference(
                    QuarkusKSqlClientContainer.class,
                    QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName));
            return QuarkusKSqlClientContainer.getKSqlClient();
        };
    }
}
