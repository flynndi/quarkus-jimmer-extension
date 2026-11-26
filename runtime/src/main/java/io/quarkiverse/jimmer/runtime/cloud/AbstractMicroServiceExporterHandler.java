package io.quarkiverse.jimmer.runtime.cloud;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.arc.WithCaching;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

public abstract class AbstractMicroServiceExporterHandler implements Handler<RoutingContext> {

    @Inject
    MicroServiceExporterClient sqlClient;

    @Inject
    @WithCaching
    Instance<JsonMapper> jsonMappers;

    protected void doHandle(HttpServerResponse response, JsonMapper jsonMapper, Object result) {
        try {
            response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .end(jsonMapper.writeValueAsString(result));
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot serialize the Jimmer microservice response", e);
        }
    }
}
