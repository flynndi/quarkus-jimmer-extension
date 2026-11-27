package io.quarkiverse.jimmer.runtime.cfg.support;

import jakarta.enterprise.inject.Any;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.DefaultUserIdGeneratorProvider;
import org.babyfish.jimmer.sql.meta.UserIdGenerator;
import org.jboss.logging.Logger;

import io.quarkus.arc.ArcContainer;

public class QuarkusUserIdGeneratorProvider extends DefaultUserIdGeneratorProvider {

    private static final Logger LOG = Logger.getLogger(QuarkusUserIdGeneratorProvider.class);

    private final ArcContainer container;
    private final SqlClientBeanAccess access;

    public QuarkusUserIdGeneratorProvider(ArcContainer container) {
        this(container, SqlClientBeanAccess.UNMANAGED);
    }

    public QuarkusUserIdGeneratorProvider(ArcContainer container, SqlClientBeanAccess access) {
        this.container = container;
        this.access = access;
    }

    @Override
    public UserIdGenerator<?> get(Class<UserIdGenerator<?>> type, JSqlClient sqlClient) throws Exception {
        if (container.select(type, Any.Literal.INSTANCE).isUnsatisfied()) {
            return super.get(type, sqlClient);
        }
        return access.lookup(container, root -> root.select(type, Any.Literal.INSTANCE).get());
    }

    @Override
    public UserIdGenerator<?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = access.get(container.instance(ref));
        if (bean instanceof UserIdGenerator<?> generator) {
            return generator;
        }
        String message = bean == null
                ? "Cannot resolve a Quarkus bean named \"" + ref + "\""
                : "Quarkus bean named \"" + ref + "\" has type \"" + bean.getClass().getName()
                        + "\", expected \"" + UserIdGenerator.class.getName() + "\"";
        // Jimmer dereferences the missing generator after catching the provider exception.
        LOG.error(message);
        throw new IllegalStateException(message);
    }
}
