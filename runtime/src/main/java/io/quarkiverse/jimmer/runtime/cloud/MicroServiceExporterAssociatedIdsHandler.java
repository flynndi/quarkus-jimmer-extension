package io.quarkiverse.jimmer.runtime.cloud;

import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.compiler.FetcherCompiler;
import org.babyfish.jimmer.sql.runtime.MicroServiceExporter;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.ext.web.RoutingContext;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class MicroServiceExporterAssociatedIdsHandler extends AbstractMicroServiceExporterHandler {

    @Override
    @ActivateRequestContext
    public void handle(RoutingContext routingContext) {
        JsonMapper objectMapper = jsonMappers.get();
        MicroServiceExporter exporter = new MicroServiceExporter(sqlClient.get());

        String prop = routingContext.request().getParam(Constant.PROP);
        String targetIdArrStr = routingContext.request().getParam(Constant.TARGET_IDS);
        String fetcherStr = routingContext.request().getParam(Constant.FETCHER);

        Fetcher<?> fetcher = FetcherCompiler.compile(fetcherStr, Thread.currentThread().getContextClassLoader());
        ImmutableProp immutableProp = fetcher.getImmutableType().getProp(prop);
        Class<?> targetIdType = immutableProp.getTargetType().getIdProp().getElementClass();
        List<?> targetIds = objectMapper.readValue(targetIdArrStr,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Classes.boxTypeOf(targetIdType)));
        // Java and Kotlin consumers both read the property names declared on Jimmer's Tuple2 constructor.
        var result = objectMapper.createArrayNode();
        for (var tuple : exporter.findByAssociatedIds(immutableProp, targetIds, fetcher)) {
            result.addObject().putPOJO("_1", tuple.get_1()).putPOJO("_2", tuple.get_2());
        }

        doHandle(routingContext.response(), objectMapper, result);
    }
}
