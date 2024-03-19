package io.quarkiverse.jimmer.runtime.cloud;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.MicroServiceExporter;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public abstract class AbstractMicroServiceExporterHandler implements Handler<RoutingContext> {

    private static final Logger log = Logger.getLogger(AbstractMicroServiceExporterHandler.class);

    protected ObjectMapper objectMapper;

    protected MicroServiceExporter exporter;

    protected boolean setup = false;

    @Override
    public abstract void handle(RoutingContext routingContext);

    protected void setup() {
        Instance<JSqlClient> jSqlClientInstance = CDI.current().select(JSqlClient.class,
                Default.Literal.INSTANCE);

        JSqlClient jSqlClient;
        if (jSqlClientInstance.isUnsatisfied()) {
            jSqlClient = null;
        } else if (jSqlClientInstance.isAmbiguous()) {
            jSqlClient = jSqlClientInstance.iterator().next();
            log.warnf("Multiple JimmerBuildTimeConfig registries present. Using %s with the built in scrape endpoint",
                    jSqlClientInstance);
        } else {
            jSqlClient = jSqlClientInstance.get();
        }

        Instance<ObjectMapper> objectMapperInstance = CDI.current().select(ObjectMapper.class,
                Default.Literal.INSTANCE);

        if (objectMapperInstance.isUnsatisfied()) {
            objectMapper = null;
        } else if (objectMapperInstance.isAmbiguous()) {
            objectMapper = objectMapperInstance.iterator().next();
            log.warnf("Multiple JimmerBuildTimeConfig registries present. Using %s with the built in scrape endpoint",
                    jSqlClientInstance);
        } else {
            objectMapper = objectMapperInstance.get();
        }

        assert jSqlClient != null;
        exporter = new MicroServiceExporter(jSqlClient);

        setup = true;
    }
}
