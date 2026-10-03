package io.quarkiverse.jimmer.runtime.cloud;

import java.nio.charset.StandardCharsets;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.sql.JSqlClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.WithCaching;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public abstract class AbstractMicroServiceExporterHandler implements Handler<RoutingContext> {

    // Resolve within the activated request context. ArC caches the selected CDI reference,
    // including a normal-scoped proxy, and owns any dependent instances until handler destruction.
    @Inject
    @WithCaching
    Instance<JSqlClient> sqlClients;

    @Inject
    @WithCaching
    Instance<ObjectMapper> objectMappers;

    protected void doHandle(HttpServerResponse response, ObjectMapper objectMapper, Object result) {
        try {
            doHandle(response, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the Jimmer microservice response", e);
        }
    }

    protected void doHandle(HttpServerResponse response, String stringResult) {
        response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .end(Buffer.buffer(stringResult.getBytes(StandardCharsets.UTF_8)));
    }
}
