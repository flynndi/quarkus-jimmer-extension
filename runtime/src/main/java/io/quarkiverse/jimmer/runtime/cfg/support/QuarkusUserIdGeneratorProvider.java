package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.Objects;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.DefaultUserIdGeneratorProvider;
import org.babyfish.jimmer.sql.meta.UserIdGenerator;
import org.jboss.logging.Logger;

import io.quarkiverse.jimmer.runtime.SqlClientBeans;

public class QuarkusUserIdGeneratorProvider extends DefaultUserIdGeneratorProvider {

    private static final Logger LOG = Logger.getLogger(QuarkusUserIdGeneratorProvider.class);

    private final SqlClientBeans beans;

    public QuarkusUserIdGeneratorProvider(SqlClientBeans beans) {
        this.beans = Objects.requireNonNull(beans, "beans must not be null");
    }

    @Override
    public UserIdGenerator<?> get(Class<UserIdGenerator<?>> type, JSqlClient sqlClient) throws Exception {
        UserIdGenerator<?> generator = beans.optionalAny(type);
        return generator != null ? generator : super.get(type, sqlClient);
    }

    @Override
    public UserIdGenerator<?> get(String ref, JSqlClient sqlClient) throws Exception {
        // Jimmer dereferences the missing generator after catching the provider exception.
        return beans.named(UserIdGenerator.class, ref, LOG::error);
    }
}
