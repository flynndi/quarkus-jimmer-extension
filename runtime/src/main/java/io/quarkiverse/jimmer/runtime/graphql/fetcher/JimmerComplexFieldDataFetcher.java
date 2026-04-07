package io.quarkiverse.jimmer.runtime.graphql.fetcher;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.meta.impl.TypedPropImpl;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.loader.graphql.Loaders;

import graphql.schema.DataFetchingEnvironment;
import io.smallrye.graphql.execution.datafetcher.PlugableDataFetcher;

public class JimmerComplexFieldDataFetcher implements PlugableDataFetcher<Object> {

    private final ImmutableProp prop;

    public JimmerComplexFieldDataFetcher(ImmutableProp prop) {
        this.prop = prop;
    }

    @Override
    public Object get(DataFetchingEnvironment dfe) {
        ImmutableSpi spi = (ImmutableSpi) dfe.getSource();
        if (spi.__isLoaded(prop.getId())) {
            Object value = spi.__get(prop.getId());
            if (value == null) {
                return null;
            }
            if (!new UnloadedContext(dfe).isUnloaded(value)) {
                return value;
            }
        }
        Loaders loaders = JimmerGraphQLSqlClientResolver.resolve(prop.getDeclaringType()).getLoaders();
        if (prop.isReference(TargetLevel.ENTITY)) {
            return loaders.reference(TypedPropImpl.Reference.of(prop)).load(spi);
        }
        if (prop.isReferenceList(TargetLevel.ENTITY)) {
            return loaders.list(new TypedPropImpl.ReferenceList<>(prop)).load(spi);
        }
        if (prop.hasTransientResolver()) {
            return loaders.value(TypedPropImpl.Scalar.of(prop)).load(spi);
        }
        return spi.__get(prop.getId());
    }
}
