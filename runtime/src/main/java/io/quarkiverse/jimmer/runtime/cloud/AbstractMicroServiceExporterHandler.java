package io.quarkiverse.jimmer.runtime.cloud;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.arc.WithCaching;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public abstract class AbstractMicroServiceExporterHandler implements Handler<RoutingContext> {

    @Inject
    MicroServiceExporterClient sqlClient;

    @Inject
    @WithCaching
    Instance<ObjectMapper> objectMappers;

    protected void doHandle(HttpServerResponse response, ObjectMapper objectMapper, Object result) {
        try {
            response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize the Jimmer microservice response", e);
        }
    }
}
