package io.quarkiverse.jimmer.runtime.cloud;

import java.util.List;

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
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

class MicroServiceExporterAssociatedIdsHandler extends AbstractMicroServiceExporterHandler {

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
            doHandle(response, result.toString());
        } else {
            requestContext.activate();
            try {
                doHandle(response, result.toString());
            } finally {
                requestContext.terminate();
            }
        }
    }
}
