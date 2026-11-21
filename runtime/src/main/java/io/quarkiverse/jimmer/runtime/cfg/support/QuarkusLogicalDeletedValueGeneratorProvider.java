package io.quarkiverse.jimmer.runtime.cfg.support;

import jakarta.enterprise.inject.Any;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.LogicalDeletedValueGeneratorProvider;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;
import org.jboss.logging.Logger;

import io.quarkus.arc.ArcContainer;

public class QuarkusLogicalDeletedValueGeneratorProvider implements LogicalDeletedValueGeneratorProvider {

    private static final Logger LOG = Logger.getLogger(QuarkusLogicalDeletedValueGeneratorProvider.class);

    private final ArcContainer container;

    public QuarkusLogicalDeletedValueGeneratorProvider(ArcContainer container) {
        this.container = container;
    }

    @Override
    public LogicalDeletedValueGenerator<?> get(Class<LogicalDeletedValueGenerator<?>> type, JSqlClient sqlClient)
            throws Exception {
        var beans = container.select(type, Any.Literal.INSTANCE);
        return beans.isUnsatisfied() ? LogicalDeletedValueGeneratorProvider.super.get(type, sqlClient) : beans.get();
    }

    @Override
    public LogicalDeletedValueGenerator<?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = container.instance(ref).get();
        if (bean instanceof LogicalDeletedValueGenerator<?> generator) {
            return generator;
        }
        String message = bean == null
                ? "Cannot resolve a Quarkus bean named \"" + ref + "\""
                : "Quarkus bean named \"" + ref + "\" has type \"" + bean.getClass().getName()
                        + "\", expected \"" + LogicalDeletedValueGenerator.class.getName() + "\"";
        // Jimmer 0.12.5 discards the provider exception when resolving generatorRef.
        LOG.error(message);
        throw new IllegalStateException(message);
    }
}
