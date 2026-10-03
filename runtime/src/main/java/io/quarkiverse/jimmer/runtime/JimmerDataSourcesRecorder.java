package io.quarkiverse.jimmer.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkiverse.jimmer.runtime.util.QuarkusSqlClientContainerUtil;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.ActiveResult;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerDataSourcesRecorder {

    private final RuntimeValue<JimmerRuntimeConfig> runtimeConfig;

    @Inject
    public JimmerDataSourcesRecorder(RuntimeValue<JimmerRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public Supplier<ActiveResult> checkActiveSupplier(String dataSourceName) {
        return () -> {
            if (runtimeConfig.getValue().dataSources().get(dataSourceName).active().filter(active -> !active).isPresent()) {
                return ActiveResult.inactive("Jimmer for datasource '" + dataSourceName + "' was explicitly deactivated");
            }
            ActiveResult dataSourceActive = AgroalDataSourceUtil.dataSourceInstance(dataSourceName)
                    .getHandle().getBean().checkActive();
            if (!dataSourceActive.value()) {
                return ActiveResult.inactive("Jimmer for datasource '" + dataSourceName
                        + "' is inactive because its datasource is inactive", dataSourceActive);
            }
            return ActiveResult.active();
        };
    }

    public Function<SyntheticCreationalContext<QuarkusJSqlClientContainer>, QuarkusJSqlClientContainer> jSqlClientContainerFunction(
            String dataSourceName) {
        return context -> {
            DataSource dataSource = context.getInjectedReference(new TypeLiteral<InjectableInstance<DataSource>>() {
            }, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
            QuarkusSqlClientProducer producer = context.getInjectedReference(QuarkusSqlClientProducer.class);
            return producer.createQuarkusJSqlClientContainer(dataSource, dataSourceName);
        };
    }

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> quarkusJSqlClientFunction(String dataSourceName) {
        return context -> {
            QuarkusJSqlClientContainer QuarkusJSqlClientContainer = context.getInjectedReference(
                    new TypeLiteral<InjectableInstance<QuarkusJSqlClientContainer>>() {
                    }, QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName)).get();
            return QuarkusJSqlClientContainer.getjSqlClient();
        };
    }

    public Function<SyntheticCreationalContext<QuarkusKSqlClientContainer>, QuarkusKSqlClientContainer> kSqlClientContainerFunction(
            String dataSourceName) {
        return context -> {
            DataSource dataSource = context.getInjectedReference(new TypeLiteral<InjectableInstance<DataSource>>() {
            }, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
            QuarkusSqlClientProducer producer = context.getInjectedReference(QuarkusSqlClientProducer.class);
            return producer.createQuarkusKSqlClientContainer(dataSource, dataSourceName);
        };
    }

    public Function<SyntheticCreationalContext<KSqlClient>, KSqlClient> quarkusKSqlClientFunction(String dataSourceName) {
        return context -> {
            QuarkusKSqlClientContainer QuarkusKSqlClientContainer = context.getInjectedReference(
                    new TypeLiteral<InjectableInstance<QuarkusKSqlClientContainer>>() {
                    }, QuarkusSqlClientContainerUtil.getQuarkusSqlClientContainerQualifier(dataSourceName)).get();
            return QuarkusKSqlClientContainer.getKSqlClient();
        };
    }
}
