package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkus.arc.Arc;
import io.quarkus.builder.Version;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.test.QuarkusUnitTest;

class DisabledJimmerIntegrationsTest {

    @RegisterExtension
    static final QuarkusUnitTest APP = new QuarkusUnitTest()
            .setForcedDependencies(List.of(Dependency.of("io.quarkus", "quarkus-scheduler", Version.getVersion())))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.redis.devservices.enabled", "false")
            .overrideConfigKey("quarkus.datasource.db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.jdbc.url", "jdbc:h2:mem:disabled-jimmer")
            .overrideConfigKey("quarkus.jimmer.enable", "false")
            .overrideConfigKey("quarkus.jimmer.trigger-type", "TRANSACTION_ONLY");

    @Inject
    Instance<JSqlClient> clients;

    @Inject
    Instance<TransactionCacheOperator> operators;

    @Inject
    Instance<TransactionCacheOperatorFlusher> flushers;

    @Test
    void disablingJimmerSkipsManagedClientsAndCacheJobsEvenWithSchedulerInstalled() {
        assertTrue(clients.isUnsatisfied());
        assertTrue(operators.isUnsatisfied());
        assertTrue(flushers.isUnsatisfied());
        assertTrue(Arc.container().beanManager().getBeans(Object.class, Any.Literal.INSTANCE).stream()
                .noneMatch(bean -> bean.getBeanClass().getName().endsWith("TransactionCacheOperatorRetryJob")));
    }
}
