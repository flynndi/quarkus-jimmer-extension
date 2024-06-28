package io.quarkiverse.jimmer.runtime.graphql;

import java.util.List;

import graphql.schema.DataFetchingEnvironment;
import io.quarkus.smallrye.graphql.runtime.spi.datafetcher.AbstractAsyncDataFetcher;
import io.smallrye.graphql.schema.model.Operation;
import io.smallrye.graphql.schema.model.Type;
import io.smallrye.mutiny.Uni;

public class JimmerAsyncDataFetcher extends AbstractAsyncDataFetcher {

    public JimmerAsyncDataFetcher(Operation operation, Type type) {
        super(operation, type);
    }

    @Override
    protected Uni<?> handleUserMethodCall(DataFetchingEnvironment dfe, Object[] transformedArguments) throws Exception {
        return null;
    }

    @Override
    protected Uni<List> handleUserBatchLoad(DataFetchingEnvironment dfe, Object[] arguments) throws Exception {
        return null;
    }
}
