package io.quarkiverse.jimmer.runtime.graphql.facade;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.meta.impl.TypedPropImpl;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.loader.graphql.Loaders;

import graphql.schema.DataFetchingEnvironment;
import io.quarkiverse.jimmer.runtime.graphql.fetcher.JimmerGraphQLSqlClientResolver;

@ApplicationScoped
public class JimmerGraphQLFacadeSupport {

    public <T> T loadFacade(JimmerGraphQLFacade<?> source, String propName, DataFetchingEnvironment env, Class<T> facadeType) {
        return JimmerGraphQLFacades.wrap(load(source, propName, env), facadeType);
    }

    public <T> List<T> loadFacadeList(
            JimmerGraphQLFacade<?> source,
            String propName,
            DataFetchingEnvironment env,
            Class<T> facadeType) {
        Object value = load(source, propName, env);
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            return JimmerGraphQLFacades.wrapList(iterable, facadeType);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public <T> T loadValue(JimmerGraphQLFacade<?> source, String propName, DataFetchingEnvironment env) {
        return (T) load(source, propName, env);
    }

    private Object load(JimmerGraphQLFacade<?> source, String propName, DataFetchingEnvironment env) {
        ImmutableSpi spi = (ImmutableSpi) source.__raw();
        ImmutableProp prop = spi.__type().getProps().get(propName);
        if (prop == null) {
            throw new IllegalArgumentException("Illegal GraphQL facade property: " + propName);
        }
        if (spi.__isLoaded(prop.getId())) {
            Object value = spi.__get(prop.getId());
            if (value == null || !new JimmerGraphQLSelectionInspector(env).isUnloaded(value)) {
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
