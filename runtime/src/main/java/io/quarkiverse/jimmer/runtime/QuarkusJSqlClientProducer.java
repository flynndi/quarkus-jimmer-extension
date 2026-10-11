package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.JSqlClient;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusCacheOperatorProvider;
import io.quarkus.arc.Arc;

/**
 * Runtime factory used by the synthetic SQL client beans.
 * Configuration is constructor-injected by ArC; the returned clients have completed Jimmer initialization.
 *
 * @author <a href="mailto:lixuan0520@gmail.com">flynndi</a>
 */
public class QuarkusJSqlClientProducer {

    private final JimmerRuntimeConfig jimmerRuntimeConfig;

    private final JimmerBuildTimeConfig jimmerBuildTimeConfig;

    public QuarkusJSqlClientProducer(JimmerRuntimeConfig jimmerRuntimeConfig, JimmerBuildTimeConfig jimmerBuildTimeConfig) {
        this.jimmerRuntimeConfig = jimmerRuntimeConfig;
        this.jimmerBuildTimeConfig = jimmerBuildTimeConfig;
    }

    public JSqlClient createJSqlClient(DataSource dataSource, String dataSourceName) {
        var container = Arc.container();
        return new QuarkusSqlClientFactory(container, jimmerRuntimeConfig, jimmerBuildTimeConfig,
                dataSource, dataSourceName,
                builder -> builder.setCacheOperator(QuarkusCacheOperatorProvider.find(container, dataSourceName)), false)
                .create();
    }
}
