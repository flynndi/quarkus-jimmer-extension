package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;

import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * This class is sort of a producer for {@link JQuarkusSqlClient}.
 * It isn't a CDI producer in the literal sense, but it is marked as a bean
 * and it's {@code createQuarkusJSqlClient} method is called at runtime in order to produce
 * the actual {@code JSqlClient} objects.
 * CDI scopes and qualifiers are set up at build-time, which is why this class is devoid of
 * any CDI annotations
 *
 */
public class JQuarkusSqlClientProducer {

    private final JimmerBuildTimeConfig config;

    private final ArcContainer container = Arc.container();

    private final Event<Object> event;

    public JQuarkusSqlClientProducer(JimmerBuildTimeConfig config, Event<Object> event) {
        this.config = config;
        this.event = event;
    }

    public JQuarkusSqlClientContainer createJQuarkusSqlClient(DataSource dataSource, String dataSourceName, Dialect dialect) {
        final boolean isKotlin = config.language().equalsIgnoreCase("kotlin");
        final JQuarkusSqlClient JQuarkusSqlClient = new JQuarkusSqlClient(config, dataSource, dataSourceName, container, null,
                event,
                dialect, isKotlin);
        return new JQuarkusSqlClientContainer(JQuarkusSqlClient, dataSourceName);
    }

    public KQuarkusSqlClientContainer createKQuarkusSqlClient(DataSource dataSource, String dataSourceName, Dialect dialect) {
        KSqlClient kSqlClient = SqlClients.kotlin(config, dataSource, dataSourceName, container, null, event, dialect);
        return new KQuarkusSqlClientContainer(kSqlClient, dataSourceName);
    }
}
