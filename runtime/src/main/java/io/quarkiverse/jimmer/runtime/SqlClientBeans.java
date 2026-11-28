package io.quarkiverse.jimmer.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.util.TypeLiteral;

import org.jetbrains.annotations.Nullable;

import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.DataSource.DataSourceLiteral;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

/**
 * Datasource-aware CDI bean lookup context for Jimmer SQL clients and SPI providers.
 * <p>
 * All lookups resolve metadata through {@link BeanManager} and obtain isolated instances via
 * {@link ArcContainer#instance(InjectableBean)} without retaining references in the global container
 * {@code Instance} context. Callers that own a client lifecycle may supply a {@code dependentConsumer}
 * to receive {@code @Dependent} handles for later disposal.
 */
public final class SqlClientBeans {

    private final ArcContainer container;

    private final BeanManager beanManager;

    private final String dataSourceName;

    private final DataSourceLiteral dataSourceQualifier;

    private final Consumer<InstanceHandle<?>> dependentConsumer;

    SqlClientBeans(ArcContainer container, @Nullable String dataSourceName) {
        this(container, dataSourceName, ignored -> {
        });
    }

    SqlClientBeans(
            ArcContainer container,
            @Nullable String dataSourceName,
            Consumer<InstanceHandle<?>> dependentConsumer) {
        this.container = Objects.requireNonNull(container, "container must not be null");
        this.beanManager = container.beanManager();
        this.dataSourceName = dataSourceName != null ? dataSourceName : DataSourceUtil.DEFAULT_DATASOURCE_NAME;
        this.dataSourceQualifier = new DataSourceLiteral(this.dataSourceName);
        this.dependentConsumer = Objects.requireNonNull(dependentConsumer, "dependentConsumer must not be null");
    }

    String dataSourceName() {
        return dataSourceName;
    }

    <T> @Nullable T optional(Class<T> type) {
        return optional((Type) type);
    }

    <T> @Nullable T optional(TypeLiteral<T> type) {
        return optional(type.getType());
    }

    <T> T requiredForDataSource(Class<T> type) {
        Annotation qualifier = DataSourceUtil.isDefault(dataSourceName)
                ? Default.Literal.INSTANCE
                : dataSourceQualifier;
        Bean<?> resolved = beanManager.resolve(getBeans(type, qualifier));
        if (resolved == null) {
            throw new UnsatisfiedResolutionException(
                    "No bean found for required type [" + type.getName() + "] and qualifier [" + qualifier + "]");
        }
        return Objects.requireNonNull(instantiate(resolved));
    }

    <T> List<T> matching(Class<T> type) {
        List<T> result = new ArrayList<>();
        collectMatching(container.listAll(type), new HashSet<>(), result);
        return result;
    }

    <T> List<T> matching(Class<? super T> rawType, TypeLiteral<T> parameterizedType) {
        List<T> result = new ArrayList<>();
        Set<InjectableBean<?>> seen = new HashSet<>();
        @SuppressWarnings("unchecked")
        List<InstanceHandle<T>> rawHandles = (List<InstanceHandle<T>>) (List<?>) container.listAll(rawType);
        collectMatching(rawHandles, seen, result);
        collectMatching(container.listAll(parameterizedType), seen, result);
        return result;
    }

    public <T> @Nullable T optionalAny(Class<T> type) {
        Bean<?> resolved = beanManager.resolve(getBeans(type, Any.Literal.INSTANCE));
        return resolved != null ? instantiate(resolved) : null;
    }

    public <T> T named(Class<T> expectedType, String ref) {
        return named(expectedType, ref, null);
    }

    @SuppressWarnings("unchecked")
    public <T> T named(Class<T> expectedType, String ref, @Nullable Consumer<String> errorLogger) {
        Bean<?> resolved = beanManager.resolve(getBeans(ref));
        Object instance = null;
        if (resolved != null) {
            InjectableBean<Object> injectableBean = (InjectableBean<Object>) resolved;
            InstanceHandle<Object> handle = container.instance(injectableBean);
            instance = handle.get();
            if (expectedType.isInstance(instance)) {
                if (injectableBean.getScope() == Dependent.class) {
                    dependentConsumer.accept(handle);
                }
                return expectedType.cast(instance);
            }
            if (instance != null && injectableBean.getScope() == Dependent.class) {
                handle.destroy();
            }
        }
        String message = instance == null
                ? "Cannot resolve a Quarkus bean named \"" + ref + "\""
                : "Quarkus bean named \"" + ref + "\" has type \"" + instance.getClass().getName()
                        + "\", expected \"" + expectedType.getName() + "\"";
        if (errorLogger != null) {
            errorLogger.accept(message);
        }
        throw new IllegalStateException(message);
    }

    <T> @Nullable T resolve(Class<T> type, Annotation... candidateQualifiers) {
        Set<Bean<?>> candidates = new LinkedHashSet<>();
        for (Annotation qualifier : candidateQualifiers) {
            candidates.addAll(getBeans(type, qualifier));
        }
        // Resolve the combined qualifier sets through ArC, including DefaultBean and alternative priorities.
        // Instantiate only the selected bean so unselected beans remain uninitialized.
        Bean<?> selected = beanManager.resolve(candidates);
        return selected != null ? instantiate(selected) : null;
    }

    private <T> @Nullable T optional(Type requiredType) {
        Set<Bean<?>> candidates = getBeans(requiredType, dataSourceQualifier);
        if (candidates.isEmpty()) {
            candidates = getBeans(requiredType, Default.Literal.INSTANCE);
        }
        Bean<?> resolved = beanManager.resolve(candidates);
        return resolved != null ? instantiate(resolved) : null;
    }

    private Set<Bean<?>> getBeans(Type requiredType, Annotation... qualifiers) {
        return filterActiveCandidates(beanManager.getBeans(requiredType, qualifiers));
    }

    private Set<Bean<?>> getBeans(String name) {
        return filterActiveCandidates(beanManager.getBeans(name));
    }

    private static Set<Bean<?>> filterActiveCandidates(Set<Bean<?>> matches) {
        if (matches.isEmpty()) {
            return Set.of();
        }
        Set<Bean<?>> candidates = new LinkedHashSet<>(matches.size());
        for (Bean<?> bean : matches) {
            if (!(bean instanceof InjectableBean<?> injectable && injectable.isSuppressed())) {
                candidates.add(bean);
            }
        }
        return candidates;
    }

    private <T> void collectMatching(
            List<InstanceHandle<T>> handles,
            Set<InjectableBean<?>> seen,
            List<T> destination) {
        for (InstanceHandle<T> handle : handles) {
            InjectableBean<T> bean = handle.getBean();
            // Inspect qualifiers before instantiation: unrelated datasource beans must not be instantiated.
            if (bean.getQualifiers().stream()
                    .noneMatch(q -> q instanceof DataSource ds && !dataSourceName.equals(ds.value()))
                    && seen.add(bean)) {
                T value = instantiate(bean);
                if (value != null) {
                    destination.add(value);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> @Nullable T instantiate(Bean<?> bean) {
        InjectableBean<T> injectableBean = (InjectableBean<T>) bean;
        InstanceHandle<T> handle = container.instance(injectableBean);
        T instance = handle.get();
        if (instance != null && injectableBean.getScope() == Dependent.class) {
            dependentConsumer.accept(handle);
        }
        return instance;
    }
}
