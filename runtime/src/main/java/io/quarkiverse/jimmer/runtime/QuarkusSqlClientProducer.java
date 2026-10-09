package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.CacheFactory;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClientKt;

import io.quarkiverse.jimmer.runtime.cache.QuarkusCacheFactory;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.support.QuarkusCacheOperatorProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * Runtime factory used by the synthetic SQL client beans.
 * Configuration is constructor-injected by ArC; the returned clients have completed Jimmer initialization.
 *
 * @author <a href="mailto:lixuan0520@gmail.com">flynndi</a>
 */
public class QuarkusSqlClientProducer {

    private final JimmerRuntimeConfig jimmerRuntimeConfig;

    private final JimmerBuildTimeConfig jimmerBuildTimeConfig;

    public QuarkusSqlClientProducer(JimmerRuntimeConfig jimmerRuntimeConfig, JimmerBuildTimeConfig jimmerBuildTimeConfig) {
        this.jimmerRuntimeConfig = jimmerRuntimeConfig;
        this.jimmerBuildTimeConfig = jimmerBuildTimeConfig;
    }

    public JSqlClient createJSqlClient(DataSource dataSource, String dataSourceName) {
        var container = Arc.container();
        return new QuarkusSqlClientFactory(container, jimmerRuntimeConfig, jimmerBuildTimeConfig,
                dataSource, dataSourceName, builder -> configureManagedCaching(builder, container, dataSourceName), false)
                .create();
    }

    public KSqlClient createKSqlClient(DataSource dataSource, String dataSourceName) {
        var container = Arc.container();
        return KSqlClientKt.toKSqlClient(new QuarkusSqlClientFactory(container, jimmerRuntimeConfig, jimmerBuildTimeConfig,
                dataSource, dataSourceName, builder -> configureManagedCaching(builder, container, dataSourceName), true)
                .create());
    }

    private void configureManagedCaching(JSqlClient.Builder builder, ArcContainer container, String dataSourceName) {
        var factories = container.select(CacheFactory.class,
                new io.quarkus.agroal.DataSource.DataSourceLiteral(dataSourceName));
        if (factories.isUnsatisfied()) {
            factories = container.select(CacheFactory.class);
        }
        if (!factories.isUnsatisfied()) {
            builder.setCacheFactory(QuarkusCacheFactory.adapt(factories.get()));
        }
        builder.setCacheOperator(QuarkusCacheOperatorProvider.find(container, dataSourceName));

        var flushers = container.select(TransactionCacheOperatorFlusher.class);
        if (!flushers.isUnsatisfied()) {
            // Only managed clients register automatic completion flushing; public CDI events remain independent.
            builder.addInitializers(client -> flushers.get().register(client, dataSourceName));
        }
    }
}
