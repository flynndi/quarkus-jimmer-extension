package io.quarkiverse.jimmer.runtime.cloud;

import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;

import io.quarkus.agroal.DataSource;
import io.quarkus.arc.WithCaching;
import io.quarkus.runtime.configuration.ConfigurationException;

abstract class MicroServiceExporterClient {

    private final Supplier<JSqlClient> client;

    <T> MicroServiceExporterClient(Class<T> type, BeanManager beans, Instance<T> clients, Function<T, JSqlClient> adapt) {
        Instance<T> selected = select(type, beans, clients);
        // Resolving CDI metadata at startup is safe. Obtain the client inside the handler's request context.
        client = () -> adapt.apply(selected.get());
    }

    JSqlClient get() {
        return client.get();
    }

    private static <T> Instance<T> select(Class<T> type, BeanManager beans, Instance<T> clients) {
        Instance<T> selected = clients.select(Default.Literal.INSTANCE);
        String qualifier = "@Default";
        if (selected.isUnsatisfied()) {
            Set<String> names = new TreeSet<>();
            for (var bean : beans.getBeans(type, Any.Literal.INSTANCE)) {
                for (var annotation : bean.getQualifiers()) {
                    if (annotation instanceof DataSource dataSource) {
                        names.add(dataSource.value());
                    }
                }
            }
            if (names.isEmpty()) {
                throw new ConfigurationException("The inbound Jimmer exporter requires a default or datasource-qualified "
                        + type.getSimpleName() + " bean");
            }
            if (names.size() > 1) {
                throw new ConfigurationException("Ambiguous " + type.getSimpleName() + " for the inbound Jimmer exporter: "
                        + "datasources " + names + " are available. Provide a default client.");
            }
            String name = names.iterator().next();
            selected = clients.select(new DataSource.DataSourceLiteral(name));
            qualifier = "@DataSource(\"" + name + "\")";
        }
        if (selected.isAmbiguous()) {
            throw new ConfigurationException("Ambiguous " + type.getSimpleName() + " beans for the inbound Jimmer exporter "
                    + "with qualifier " + qualifier);
        }
        return selected;
    }

    static class Java extends MicroServiceExporterClient {
        @Inject
        Java(BeanManager beans, @Any @WithCaching Instance<JSqlClient> clients) {
            super(JSqlClient.class, beans, clients, Function.identity());
        }
    }

    static class Kotlin extends MicroServiceExporterClient {
        @Inject
        Kotlin(BeanManager beans, @Any @WithCaching Instance<KSqlClient> clients) {
            super(KSqlClient.class, beans, clients, KSqlClient::getJavaClient);
        }
    }
}
