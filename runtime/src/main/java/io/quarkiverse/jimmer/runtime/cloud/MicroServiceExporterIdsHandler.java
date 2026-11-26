package io.quarkiverse.jimmer.runtime.cloud;

import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.compiler.FetcherCompiler;
import org.babyfish.jimmer.sql.runtime.MicroServiceExporter;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.ext.web.RoutingContext;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class MicroServiceExporterIdsHandler extends AbstractMicroServiceExporterHandler {

    @Override
    @ActivateRequestContext
    public void handle(RoutingContext routingContext) {
        JsonMapper objectMapper = jsonMappers.get();
        MicroServiceExporter exporter = new MicroServiceExporter(sqlClient.get());

        String idArrStr = routingContext.request().getParam(Constant.IDS);
        String fetcherStr = routingContext.request().getParam(Constant.FETCHER);

        Fetcher<?> fetcher = FetcherCompiler.compile(fetcherStr, Thread.currentThread().getContextClassLoader());
        Class<?> idType = fetcher.getImmutableType().getIdProp().getElementClass();
        List<?> ids = objectMapper.readValue(idArrStr,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Classes.boxTypeOf(idType)));

        List<ImmutableSpi> result = exporter.findByIds(ids, fetcher);

        doHandle(routingContext.response(), objectMapper, result);
    }
}
