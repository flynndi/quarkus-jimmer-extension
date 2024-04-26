package io.quarkiverse.jimmer.it.config;

import java.time.ZoneId;
import java.util.function.Consumer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.it.Constant;
import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkus.arc.Unremovable;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TestConsumerZone {

    @Test
    void testConsumer() {
        JSqlClientImplementor defaultJSqlClientImplementor = (JSqlClientImplementor) Jimmer.getDefaultJSqlClient();
        ZoneId zoneId = defaultJSqlClientImplementor.getZoneId();

        JSqlClientImplementor jSqlClientImplementorDB2 = (JSqlClientImplementor) Jimmer.getJSqlClient(Constant.DATASOURCE2);
        ZoneId zoneIdDB2 = jSqlClientImplementorDB2.getZoneId();

        Assertions.assertEquals(zoneId, ZoneId.of("Asia/Chongqing"));
        Assertions.assertEquals(zoneIdDB2, ZoneId.of("Asia/Chongqing"));
    }

    @ApplicationScoped
    public static class ZoneIdConfig {

        @Singleton
        @Unremovable
        public Consumer<JSqlClient.Builder> jSqlClientZoneIdConfigurer() {
            return builder -> builder.setZoneId(ZoneId.of("Asia/Chongqing"));
        }
    }
}
