package io.quarkiverse.jimmer.runtime.graphql.facade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    private static final Object UNLOADED = new Object();

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

    public <T> List<T> loadFacadeBatch(
            List<? extends JimmerGraphQLFacade<?>> sources,
            String propName,
            DataFetchingEnvironment env,
            Class<T> facadeType) {
        List<Object> values = loadBatch(sources, propName, env);
        List<T> wrappedValues = new ArrayList<>(values.size());
        for (Object value : values) {
            wrappedValues.add(JimmerGraphQLFacades.wrap(value, facadeType));
        }
        return wrappedValues;
    }

    public <T> List<List<T>> loadFacadeListBatch(
            List<? extends JimmerGraphQLFacade<?>> sources,
            String propName,
            DataFetchingEnvironment env,
            Class<T> facadeType) {
        List<Object> values = loadBatch(sources, propName, env);
        List<List<T>> wrappedValues = new ArrayList<>(values.size());
        for (Object value : values) {
            if (value == null) {
                wrappedValues.add(null);
            } else if (value instanceof Iterable<?> iterable) {
                wrappedValues.add(JimmerGraphQLFacades.wrapList(iterable, facadeType));
            } else {
                wrappedValues.add(List.of());
            }
        }
        return wrappedValues;
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> loadValueBatch(
            List<? extends JimmerGraphQLFacade<?>> sources,
            String propName,
            DataFetchingEnvironment env) {
        return (List<T>) (List<?>) loadBatch(sources, propName, env);
    }

    private Object load(JimmerGraphQLFacade<?> source, String propName, DataFetchingEnvironment env) {
        ImmutableSpi spi = (ImmutableSpi) source.__raw();
        ImmutableProp prop = immutableProp(spi, propName);
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

    private List<Object> loadBatch(
            List<? extends JimmerGraphQLFacade<?>> sources,
            String propName,
            DataFetchingEnvironment env) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }

        ImmutableProp prop = null;
        List<ImmutableSpi> rawSources = new ArrayList<>(sources.size());
        for (JimmerGraphQLFacade<?> source : sources) {
            ImmutableSpi spi = source == null ? null : (ImmutableSpi) source.__raw();
            rawSources.add(spi);
            if (spi != null && prop == null) {
                prop = immutableProp(spi, propName);
            }
        }
        if (prop == null) {
            return new ArrayList<>(Collections.nCopies(rawSources.size(), null));
        }

        JimmerGraphQLSelectionInspector inspector = new JimmerGraphQLSelectionInspector(env);
        List<Object> values = new ArrayList<>(Collections.nCopies(rawSources.size(), null));
        List<ImmutableSpi> unloadedSources = new ArrayList<>();
        List<Integer> unloadedIndexes = new ArrayList<>();

        for (int i = 0; i < rawSources.size(); i++) {
            ImmutableSpi spi = rawSources.get(i);
            if (spi == null) {
                continue;
            }
            Object value = loadedValue(spi, prop, inspector);
            if (value == UNLOADED) {
                unloadedSources.add(spi);
                unloadedIndexes.add(i);
            } else {
                values.set(i, value);
            }
        }

        if (unloadedSources.isEmpty()) {
            return values;
        }

        Map<?, ?> loadedValues = batchLoad(prop, unloadedSources);
        for (int i = 0; i < unloadedSources.size(); i++) {
            values.set(unloadedIndexes.get(i), loadedValues.get(unloadedSources.get(i)));
        }
        return values;
    }

    private ImmutableProp immutableProp(ImmutableSpi spi, String propName) {
        ImmutableProp prop = spi.__type().getProps().get(propName);
        if (prop == null) {
            throw new IllegalArgumentException("Illegal GraphQL facade property: " + propName);
        }
        return prop;
    }

    private Object loadedValue(ImmutableSpi spi, ImmutableProp prop, JimmerGraphQLSelectionInspector inspector) {
        if (spi.__isLoaded(prop.getId())) {
            Object value = spi.__get(prop.getId());
            if (value == null || !inspector.isUnloaded(value)) {
                return value;
            }
        }
        return UNLOADED;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Map<?, ?> batchLoad(ImmutableProp prop, Collection<ImmutableSpi> sources) {
        Loaders loaders = JimmerGraphQLSqlClientResolver.resolve(prop.getDeclaringType()).getLoaders();
        if (prop.isReference(TargetLevel.ENTITY)) {
            return loaders.reference(TypedPropImpl.Reference.of(prop)).batchLoad((Collection) sources);
        }
        if (prop.isReferenceList(TargetLevel.ENTITY)) {
            return loaders.list(new TypedPropImpl.ReferenceList<>(prop)).batchLoad((Collection) sources);
        }
        if (prop.hasTransientResolver()) {
            return loaders.value(TypedPropImpl.Scalar.of(prop)).batchLoad((Collection) sources);
        }
        return Collections.emptyMap();
    }
}
