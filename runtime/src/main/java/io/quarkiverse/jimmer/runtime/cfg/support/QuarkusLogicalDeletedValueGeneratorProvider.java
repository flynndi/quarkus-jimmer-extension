package io.quarkiverse.jimmer.runtime.cfg.support;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.LogicalDeletedValueGeneratorProvider;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;

import io.quarkus.arc.ArcContainer;

public class QuarkusLogicalDeletedValueGeneratorProvider implements LogicalDeletedValueGeneratorProvider {

    private final ArcContainer container;

    public QuarkusLogicalDeletedValueGeneratorProvider(ArcContainer container) {
        this.container = container;
    }

    @Override
    public LogicalDeletedValueGenerator<?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = container.instance(ref).get();
        if (!(bean instanceof LogicalDeletedValueGenerator<?>)) {
            throw new IllegalStateException(
                    "The expected type of quarkus bean named \"ref\" is \"" +
                            LogicalDeletedValueGenerator.class.getName() +
                            "\", but the actual type is + \"" +
                            bean.getClass().getName() +
                            "\"");
        }
        return (LogicalDeletedValueGenerator<?>) bean;
    }
}
