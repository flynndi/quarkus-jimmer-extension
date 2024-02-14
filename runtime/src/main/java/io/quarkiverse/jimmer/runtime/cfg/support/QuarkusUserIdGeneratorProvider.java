package io.quarkiverse.jimmer.runtime.cfg.support;

import java.util.List;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.di.DefaultUserIdGeneratorProvider;
import org.babyfish.jimmer.sql.meta.UserIdGenerator;

import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;

public class QuarkusUserIdGeneratorProvider extends DefaultUserIdGeneratorProvider {

    private final ArcContainer container;

    public QuarkusUserIdGeneratorProvider(ArcContainer container) {
        this.container = container;
    }

    @Override
    public UserIdGenerator<?> get(Class<UserIdGenerator<?>> type, JSqlClient sqlClient) throws Exception {
        List<InstanceHandle<UserIdGenerator<?>>> instanceHandles = container.listAll(type);
        if (instanceHandles.isEmpty()) {
            return super.get(type, sqlClient);
        }
        if (instanceHandles.size() > 1) {
            throw new IllegalStateException("Two many quarkus beans whose type is \"" + type.getName() + "\"");
        }
        return instanceHandles.iterator().next().get();
    }

    @Override
    public UserIdGenerator<?> get(String ref, JSqlClient sqlClient) throws Exception {
        Object bean = container.instance(ref).get();
        if (!(bean instanceof UserIdGenerator<?>)) {
            throw new IllegalStateException(
                    "The expected type of quarkus bean named \"ref\" is \"" +
                            UserIdGenerator.class.getName() +
                            "\", but the actual type is + \"" +
                            bean.getClass().getName() +
                            "\"");
        }
        return (UserIdGenerator<?>) bean;
    }
}
