package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * This class is sort of a producer for {@link JQuarkusSqlClient}.
 * It isn't a CDI producer in the literal sense, but it is marked as a bean
 * and it's {@code createQuarkusJSqlClient} or {@code createQuarkusKSqlClient}
 * method is called at runtime in order to produce
 * the actual {@code JSqlClient} or {@code KSqlClient} objects.
 * CDI scopes and qualifiers are set up at build-time, which is why this class is devoid of
 * any CDI annotations
 * <p>
 * * @author <a href="mailto:lixuan0520@gmail.com">flynndi</a>
 */
public class QuarkusSqlClientProducer {

    private final JimmerBuildTimeConfig config;

    private final ArcContainer container = Arc.container();

    private final Event<Object> event;

    public QuarkusSqlClientProducer(JimmerBuildTimeConfig config, Event<Object> event) {
        this.config = config;
        this.event = event;
    }

    public QuarkusJSqlClientContainer createQuarkusJSqlClient(DataSource dataSource, String dataSourceName, Dialect dialect) {
        final JSqlClient jSqlClient = SqlClients.java(config, dataSource, dataSourceName, container, null,
                event,
                dialect);
        return new QuarkusJSqlClientContainer(jSqlClient, dataSourceName);
    }

    public QuarkusKSqlClientContainer createQuarkusKSqlClient(DataSource dataSource, String dataSourceName, Dialect dialect) {
        final KSqlClient kSqlClient = SqlClients.kotlin(config, dataSource, dataSourceName, container, null, event, dialect);
        return new QuarkusKSqlClientContainer(kSqlClient, dataSourceName);
    }
}
