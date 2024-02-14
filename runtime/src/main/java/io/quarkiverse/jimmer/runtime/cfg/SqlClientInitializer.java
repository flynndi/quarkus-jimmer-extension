package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;

import jakarta.enterprise.event.Observes;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.kt.impl.KSqlClientImplementor;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import io.quarkus.arc.All;
import io.quarkus.runtime.StartupEvent;

public class SqlClientInitializer {

    void init(@Observes StartupEvent event, @All List<JSqlClient> javaSqlClients, @All List<KSqlClient> kotlinSqlClients) {
        for (JSqlClient sqlClient : javaSqlClients) {
            ((JSqlClientImplementor) sqlClient).initialize();
        }
        for (KSqlClient sqlClient : kotlinSqlClients) {
            ((KSqlClientImplementor) sqlClient).initialize();
        }
    }
}
