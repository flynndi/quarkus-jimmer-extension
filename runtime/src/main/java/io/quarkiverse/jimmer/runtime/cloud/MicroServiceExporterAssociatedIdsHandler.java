package io.quarkiverse.jimmer.runtime.cloud;

import java.nio.charset.StandardCharsets;
import java.util.List;

import jakarta.ws.rs.core.MediaType;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.ast.tuple.Tuple2;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.compiler.FetcherCompiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

public class MicroServiceExporterAssociatedIdsHandler extends AbstractMicroServiceExporterHandler {

    @Override
    public void handle(RoutingContext routingContext) {
        if (!setup) {
            setup();
        }

        String prop = routingContext.request().getParam(Constant.PROP);
        String targetIdArrStr = routingContext.request().getParam(Constant.TARGET_IDS);
        String fetcherStr = routingContext.request().getParam(Constant.FETCHER);

        Fetcher<?> fetcher = FetcherCompiler.compile(fetcherStr, Thread.currentThread().getContextClassLoader());
        ImmutableProp immutableProp = fetcher.getImmutableType().getProp(prop);
        Class<?> targetIdType = immutableProp.getTargetType().getIdProp().getElementClass();
        List<?> targetIds = null;
        try {
            targetIds = objectMapper.readValue(
                    targetIdArrStr,
                    CollectionType.construct(
                            List.class,
                            null,
                            null,
                            null,
                            SimpleType.constructUnsafe(Classes.boxTypeOf(targetIdType))));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        List<Tuple2<Object, ImmutableSpi>> result = exporter.findByAssociatedIds(immutableProp, targetIds, fetcher);

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

    private void doHandle(HttpServerResponse response, List<Tuple2<Object, ImmutableSpi>> result) {
        response.putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .end(Buffer.buffer(result.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
