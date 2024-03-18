package io.quarkiverse.jimmer.runtime.cloud;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.compiler.FetcherCompiler;
import org.babyfish.jimmer.sql.runtime.MicroServiceExporter;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class MicroServiceExporterHandler implements Handler<RoutingContext> {

    private static final Logger log = Logger.getLogger(MicroServiceExporterHandler.class);

    private ObjectMapper objectMapper;

    private MicroServiceExporter exporter;

    private boolean setup = false;

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        String idArrStr = routingContext.request().getParam(Constant.IDS);
        String fetcherStr = routingContext.request().getParam(Constant.FETCHER);

        Fetcher<?> fetcher = FetcherCompiler.compile(fetcherStr, Thread.currentThread().getContextClassLoader());
        Class<?> idType = fetcher.getImmutableType().getIdProp().getElementClass();
        List<?> ids = null;
        try {
            ids = objectMapper.readValue(
                    idArrStr,
                    CollectionType.construct(
                            List.class,
                            null,
                            null,
                            null,
                            SimpleType.constructUnsafe(Classes.boxTypeOf(idType))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        List<ImmutableSpi> result = exporter.findByIds(ids, fetcher);

        HttpServerResponse response = routingContext.response();
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            doHandle(response, result);
        } else {
            requestContext.activate();
            try {
                doHandle(response, result);
            } finally {
                requestContext.terminate();
            }
        }
    }

    private void doHandle(HttpServerResponse response, List<ImmutableSpi> result) {
        response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .end(Buffer.buffer(result.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private void setup() {
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
