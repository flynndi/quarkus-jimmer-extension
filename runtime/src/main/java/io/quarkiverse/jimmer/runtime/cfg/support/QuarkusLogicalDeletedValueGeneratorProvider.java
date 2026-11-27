package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.Objects;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.LogicalDeletedValueGeneratorProvider;
import org.babyfish.jimmer.sql.meta.LogicalDeletedValueGenerator;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.internal.SqlClientBeans;

public class QuarkusLogicalDeletedValueGeneratorProvider implements LogicalDeletedValueGeneratorProvider {

    private static final Logger LOG = Logger.getLogger(QuarkusLogicalDeletedValueGeneratorProvider.class);

    private final SqlClientBeans beans;

    public QuarkusLogicalDeletedValueGeneratorProvider(SqlClientBeans beans) {
        this.beans = Objects.requireNonNull(beans, "beans must not be null");
    }

    @Override
    public LogicalDeletedValueGenerator<?> get(Class<LogicalDeletedValueGenerator<?>> type, JSqlClient sqlClient)
            throws Exception {
        LogicalDeletedValueGenerator<?> generator = beans.optionalAny(type);
        return generator != null ? generator : LogicalDeletedValueGeneratorProvider.super.get(type, sqlClient);
    }

    @Override
    public LogicalDeletedValueGenerator<?> get(String ref, JSqlClient sqlClient) throws Exception {
        // Jimmer 0.12.5 discards the provider exception when resolving generatorRef.
        return beans.named(LogicalDeletedValueGenerator.class, ref, LOG::error);
    }
}
