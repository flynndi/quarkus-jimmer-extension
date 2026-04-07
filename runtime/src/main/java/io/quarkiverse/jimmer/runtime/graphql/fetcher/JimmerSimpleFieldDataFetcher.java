package io.quarkiverse.jimmer.runtime.graphql.fetcher;

import org.babyfish.jimmer.meta.PropId;
import org.babyfish.jimmer.runtime.ImmutableSpi;

import graphql.schema.DataFetchingEnvironment;
import io.smallrye.graphql.execution.datafetcher.PlugableDataFetcher;

public class JimmerSimpleFieldDataFetcher implements PlugableDataFetcher<Object> {

    private final PropId propId;

    public JimmerSimpleFieldDataFetcher(PropId propId) {
        this.propId = propId;
    }

    @Override
    public Object get(DataFetchingEnvironment dfe) {
        return ((ImmutableSpi) dfe.getSource()).__get(propId);
    }
}
