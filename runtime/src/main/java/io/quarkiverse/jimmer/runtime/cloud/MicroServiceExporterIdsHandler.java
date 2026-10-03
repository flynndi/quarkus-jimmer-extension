package io.quarkiverse.jimmer.runtime.cloud;

import java.util.List;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.impl.util.Classes;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.compiler.FetcherCompiler;
import org.babyfish.jimmer.sql.runtime.MicroServiceExporter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.SimpleType;

import io.quarkiverse.jimmer.runtime.util.Constant;
import io.vertx.ext.web.RoutingContext;

@Singleton
public class MicroServiceExporterIdsHandler extends AbstractMicroServiceExporterHandler {

    @Override
    @ActivateRequestContext
    public void handle(RoutingContext routingContext) {
        ObjectMapper objectMapper = objectMappers.get();
        MicroServiceExporter exporter = new MicroServiceExporter(sqlClients.get());

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

        doHandle(routingContext.response(), objectMapper, result);
    }
}
