package io.quarkiverse.jimmer.runtime;

import javax.sql.DataSource;

import jakarta.enterprise.event.Event;

import org.babyfish.jimmer.sql.dialect.Dialect;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;

/**
 * This class is sort of a producer for {@link QuarkusJSqlClient}.
 * It isn't a CDI producer in the literal sense, but it is marked as a bean
 * and it's {@code createBuilder} method is called at runtime in order to produce
 * the actual {@code JSqlClient} objects.
 * CDI scopes and qualifiers are set up at build-time, which is why this class is devoid of
 * any CDI annotations
 *
 */
public class QuarkusJSqlClientProducer {

    private final JimmerBuildTimeConfig config;

    private final ArcContainer container = Arc.container();

    private final Event<Object> event;

    public QuarkusJSqlClientProducer(JimmerBuildTimeConfig config, Event<Object> event) {
        this.config = config;
        this.event = event;
    }

    public QuarkusJSqlClientContainer createQuarkusJSqlClient(DataSource dataSource, String dataSourceName, Dialect dialect) {
        final boolean isKotlin = config.language().equalsIgnoreCase("kotlin");
        final QuarkusJSqlClient quarkusJSqlClient = new QuarkusJSqlClient(config, dataSource, dataSourceName, container, event,
                dialect, isKotlin);
        return new QuarkusJSqlClientContainer(quarkusJSqlClient, dataSourceName);
    }
}
