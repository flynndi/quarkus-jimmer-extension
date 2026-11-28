package io.quarkiverse.jimmer.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.sql.DataSource;

import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClientKt;

import io.quarkiverse.jimmer.runtime.cache.impl.LazyTransactionCacheOperator;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkiverse.jimmer.runtime.cfg.JimmerDataSourceRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.dialect.DialectResolver;
import io.quarkus.agroal.runtime.AgroalDataSourceUtil;
import io.quarkus.arc.ActiveResult;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerRecorder {

    private final RuntimeValue<JimmerRuntimeConfig> runtimeConfig;

    @Inject
    public JimmerRecorder(RuntimeValue<JimmerRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    public void registerDataSourceDialects(Map<String, String> dbKinds, Map<String, String> dbVersions) {
        DialectResolver.registerDataSources(dbKinds, dbVersions);
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

    public void validateActiveDataSources(List<String> dataSourceNames) {
        Map<String, JimmerDataSourceRuntimeConfig> activeConfigs = new LinkedHashMap<>();
        for (String name : dataSourceNames) {
            JimmerDataSourceRuntimeConfig config = runtimeConfig.getValue().dataSources().get(name);
            if (config.active().filter(active -> !active).isPresent()) {
                continue;
            }
            // Bean metadata is sufficient: neither get() nor a JDBC connection is needed.
            if (AgroalDataSourceUtil.dataSourceInstance(name).getHandle().getBean().isActive()) {
                activeConfigs.put(name, config);
            }
        }
        JimmerConfigValidator.validateDataSources(activeConfigs);
    }

    public Function<SyntheticCreationalContext<JSqlClient>, JSqlClient> jSqlClientFunction(String dataSourceName) {
        return context -> SqlClientDestroyer.create(context, dataSourceName, beans -> {
            DataSource dataSource = context.getInjectedReference(new TypeLiteral<InjectableInstance<DataSource>>() {
            }, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
            QuarkusSqlClientFactory factory = context.getInjectedReference(QuarkusSqlClientFactory.class);
            return factory.createManagedSqlClient(dataSource, beans, false);
        });
    }

    public Function<SyntheticCreationalContext<KSqlClient>, KSqlClient> kSqlClientFunction(String dataSourceName) {
        // Use an anonymous class rather than a lambda so javac does not emit a private KSqlClient-returning
        // synthetic method on JimmerRecorder, keeping jimmer-sql-kotlin optional on Java-only classpaths.
        return new Function<>() {
            @Override
            public KSqlClient apply(SyntheticCreationalContext<KSqlClient> context) {
                return SqlClientDestroyer.create(context, dataSourceName, beans -> {
                    DataSource dataSource = context.getInjectedReference(
                            new TypeLiteral<InjectableInstance<DataSource>>() {
                            }, AgroalDataSourceUtil.qualifier(dataSourceName)).get();
                    QuarkusSqlClientFactory factory = context.getInjectedReference(QuarkusSqlClientFactory.class);
                    return KSqlClientKt.toKSqlClient(factory.createManagedSqlClient(dataSource, beans, true));
                });
            }
        };
    }

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionJCacheOperatorFunction(
            String dataSourceName) {
        return context -> new LazyTransactionCacheOperator(() -> context.getInjectedReference(
                new TypeLiteral<InjectableInstance<JSqlClient>>() {
                }, AgroalDataSourceUtil.qualifier(dataSourceName)).get());
    }

    public Function<SyntheticCreationalContext<TransactionCacheOperator>, TransactionCacheOperator> transactionKCacheOperatorFunction(
            String dataSourceName) {
        return new Function<>() {
            @Override
            public TransactionCacheOperator apply(SyntheticCreationalContext<TransactionCacheOperator> context) {
                return new LazyTransactionCacheOperator(() -> context.getInjectedReference(
                        new TypeLiteral<InjectableInstance<KSqlClient>>() {
                        }, AgroalDataSourceUtil.qualifier(dataSourceName)).get().getJavaClient());
            }
        };
    }
}
