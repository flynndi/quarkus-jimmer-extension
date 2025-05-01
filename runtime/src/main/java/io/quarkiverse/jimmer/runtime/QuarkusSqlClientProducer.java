package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkus.arc.Arc;

/**
 * This class is sort of a producer for {@link JQuarkusSqlClient}.
 * It isn't a CDI producer in the literal sense, but it is marked as a bean,
 * and it's {@link #createQuarkusJSqlClientContainer} or
 * {@link #createQuarkusKSqlClientContainer}
 * method is called at runtime in order to produce
 * the actual {@link JSqlClient} or {@link KSqlClient} objects.
 * CDI scopes and qualifiers are set up at build-time, which is why this class is devoid of
 * any CDI annotations
 * <p>
 *
 * @author <a href="mailto:lixuan0520@gmail.com">flynndi</a>
 */
public class QuarkusSqlClientProducer {

    private final JimmerRuntimeConfig jimmerRuntimeConfig;

    public QuarkusSqlClientProducer(JimmerRuntimeConfig jimmerRuntimeConfig) {
        this.jimmerRuntimeConfig = jimmerRuntimeConfig;
    }

    public QuarkusJSqlClientContainer createQuarkusJSqlClientContainer(DataSource dataSource, String dataSourceName) {
        final JSqlClient jSqlClient = SqlClients.java(Arc.container(), dataSource, dataSourceName);
        return new QuarkusJSqlClientContainer(jSqlClient, dataSourceName);
    }

    public QuarkusKSqlClientContainer createQuarkusKSqlClientContainer(DataSource dataSource, String dataSourceName) {
        final KSqlClient kSqlClient = SqlClients.kotlin(Arc.container(), dataSource, dataSourceName);
        return new QuarkusKSqlClientContainer(kSqlClient, dataSourceName);
    }
}
