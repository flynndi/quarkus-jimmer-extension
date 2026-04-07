package io.quarkiverse.jimmer.runtime.graphql.spi;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TargetLevel;

import io.quarkiverse.jimmer.runtime.graphql.fetcher.JimmerComplexFieldDataFetcher;
import io.quarkiverse.jimmer.runtime.graphql.fetcher.JimmerSimpleFieldDataFetcher;
import io.smallrye.graphql.execution.datafetcher.PlugableDataFetcher;
import io.smallrye.graphql.schema.model.Field;
import io.smallrye.graphql.schema.model.Reference;
import io.smallrye.graphql.schema.model.Type;
import io.smallrye.graphql.spi.ClassloadingService;
import io.smallrye.graphql.spi.DataFetcherService;

public class JimmerGraphQLDataFetcherService implements DataFetcherService {

    @Override
    public Integer getPriority() {
        return 100;
    }

    @Override
    public PlugableDataFetcher<?> getFieldDataFetcher(Field field, Type type, Reference owner) {
        ImmutableProp prop = immutableProp(field, owner);
        if (prop == null) {
            return null;
        }
        if (prop.isAssociation(TargetLevel.ENTITY) || prop.hasTransientResolver()) {
            return new JimmerComplexFieldDataFetcher(prop);
        }
        return new JimmerSimpleFieldDataFetcher(prop.getId());
    }

    private ImmutableProp immutableProp(Field field, Reference owner) {
        Class<?> ownerClass = ClassloadingService.get().loadClass(owner.getClassName());
        ImmutableType immutableType = ImmutableType.tryGet(ownerClass);
        if (immutableType == null || !immutableType.isEntity()) {
            return null;
        }
        ImmutableProp prop = immutableType.getProps().get(field.getPropertyName());
        if (prop != null) {
            return prop;
        }
        return immutableType.getProps().get(field.getName());
    }
}
