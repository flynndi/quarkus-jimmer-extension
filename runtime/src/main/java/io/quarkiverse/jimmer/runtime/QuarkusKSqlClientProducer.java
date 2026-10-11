package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClientKt;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusCacheOperatorProvider;
import io.quarkus.arc.Arc;

/** Registered only for Kotlin clients, keeping optional Kotlin types out of the Java CDI bean. */
public class QuarkusKSqlClientProducer {

    private final JimmerRuntimeConfig runtimeConfig;
    private final JimmerBuildTimeConfig buildTimeConfig;

    public QuarkusKSqlClientProducer(JimmerRuntimeConfig runtimeConfig, JimmerBuildTimeConfig buildTimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.buildTimeConfig = buildTimeConfig;
    }

    public KSqlClient createKSqlClient(DataSource dataSource, String dataSourceName) {
        var container = Arc.container();
        return KSqlClientKt.toKSqlClient(new QuarkusSqlClientFactory(container, runtimeConfig, buildTimeConfig,
                dataSource, dataSourceName,
                builder -> builder.setCacheOperator(QuarkusCacheOperatorProvider.find(container, dataSourceName)), true)
                .create());
    }
}
