package io.quarkiverse.jimmer.runtime.devui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.Caches;
import org.babyfish.jimmer.sql.cache.CachesImpl;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.babyfish.jimmer.sql.transaction.TxConnectionManager;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.InjectableBean;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

/** Read-only diagnostics. Registered as a bean only by the local Dev UI JSON-RPC integration. */
public class JimmerDevUIService {

    private final JimmerBuildTimeConfig buildTimeConfig;
    private final JimmerRuntimeConfig runtimeConfig;

    @Inject
    public JimmerDevUIService(JimmerBuildTimeConfig buildTimeConfig, JimmerRuntimeConfig runtimeConfig) {
        this.buildTimeConfig = buildTimeConfig;
        this.runtimeConfig = runtimeConfig;
    }

    public Map<String, Object> getClients() {
        List<Map<String, Object>> clients = new ArrayList<>();
        if (buildTimeConfig.enable()) {
            for (String name : clientNames()) {
                clients.add(snapshot(name).summary());
            }
        }
        String interval = runtimeConfig.transactionCacheOperatorFixedDelay();
        return Map.of("enabled", buildTimeConfig.enable(), "language", buildTimeConfig.language(), "clients", clients,
                "cacheRetry", Map.of("interval", interval,
                        "intervalEnabled", !"off".equalsIgnoreCase(interval) && !"disabled".equalsIgnoreCase(interval),
                        // Read configuration only: resolving a Scheduler bean could initialize an optional integration.
                        "schedulerEnabled", ConfigProvider.getConfig()
                                .getOptionalValue("quarkus.scheduler.enabled", Boolean.class).orElse(true)));
    }

    public Map<String, Object> getClient(String name) {
        String selectedName = name == null ? DataSourceUtil.DEFAULT_DATASOURCE_NAME : name;
        if (!buildTimeConfig.enable()) {
            Map<String, Object> result = summary(selectedName, null, "unavailable", "Jimmer is disabled.");
            result.put("configured", Map.of());
            result.put("actual", null);
            result.put("actualDetail", "Jimmer is disabled.");
            return result;
        }
        boolean known = clientNames().contains(selectedName);
        Snapshot snapshot = known ? snapshot(selectedName)
                : new Snapshot(
                        summary(selectedName, null, "unavailable", "No registered or configured client matches this name."),
                        null);
        Map<String, Object> result = snapshot.summary();
        result.put("configured", known ? configured(selectedName) : Map.of());
        result.put("actual", null);
        result.put("actualDetail", "Actual settings are available after the application initializes this client.");
        if (snapshot.instance() != null) {
            // Only Jimmer's own implementations have known read-only getters. Do not invoke arbitrary custom clients.
            JSqlClientImplementor client = nativeClient(snapshot.instance());
            if (client == null) {
                result.put("actualDetail",
                        "The initialized client has a custom implementation; actual settings are not inspected.");
            } else {
                try {
                    Map<String, Object> actual = actual(client);
                    result.put("actual", actual);
                    result.put("actualDetail", actual.get("objectCacheCount") == null
                            ? "Snapshot of the existing CDI client; cache counts are unavailable for its custom cache registry."
                            : "Snapshot of the existing CDI client; no client was initialized by this request.");
                } catch (RuntimeException exception) {
                    result.put("actualDetail", "Actual settings could not be read; no database operation was requested.");
                }
            }
        } else if (!"uninitialized".equals(result.get("state"))) {
            result.put("actualDetail", result.get("detail"));
        }
        return result;
    }

    private Set<String> clientNames() {
        Set<String> names = new TreeSet<>();
        names.addAll(buildTimeConfig.dataSources().keySet());
        names.addAll(runtimeConfig.dataSources().keySet());
        // Config mappings automatically include the default group even in a named-datasource-only application.
        names.remove(DataSourceUtil.DEFAULT_DATASOURCE_NAME);
        try {
            for (var handle : Arc.container().select(clientType(), Any.Literal.INSTANCE).handles()) {
                // Handles are lazy. Reading bean qualifiers never obtains the bean instance.
                InjectableBean<?> bean = handle.getBean();
                for (var qualifier : bean.getQualifiers()) {
                    if (qualifier instanceof DataSource dataSource) {
                        names.add(dataSource.value());
                    } else if (qualifier instanceof Default) {
                        names.add(DataSourceUtil.DEFAULT_DATASOURCE_NAME);
                    }
                }
            }
        } catch (RuntimeException exception) {
            // A dev-mode restart can temporarily remove the container; configured names remain inspectable.
        }
        return names;
    }

    private Class<?> clientType() {
        return "kotlin".equals(buildTimeConfig.language()) ? KSqlClient.class : JSqlClient.class;
    }

    private Snapshot snapshot(String name) {
        try {
            var clients = Arc.container().select(clientType(), DataSourceUtil.isDefault(name)
                    ? Default.Literal.INSTANCE
                    : new DataSource.DataSourceLiteral(name));
            if (clients.isUnsatisfied()) {
                return new Snapshot(summary(name, null, "unavailable", "No CDI SQL client is registered for this datasource."),
                        null);
            }
            if (clients.isAmbiguous()) {
                return new Snapshot(summary(name, null, "ambiguous", "Multiple CDI SQL clients match this datasource."), null);
            }
            InjectableBean<?> bean = clients.getHandle().getBean();
            if (!bean.checkActive().value()) {
                return new Snapshot(summary(name, false, "inactive", "Jimmer or its datasource is inactive."), null);
            }
            if (bean.getScope() != ApplicationScoped.class && bean.getScope() != Singleton.class) {
                return new Snapshot(
                        summary(name, true, "unavailable", "The client scope does not support shared-instance diagnostics."),
                        null);
            }
            var context = Arc.container().getActiveContext(bean.getScope());
            if (context == null) {
                return new Snapshot(summary(name, true, "unavailable", "The client context is not active."), null);
            }
            // The single-argument Context.get only returns an existing instance; no CreationalContext is supplied.
            Object instance = context.get(bean);
            return new Snapshot(summary(name, true, instance == null ? "uninitialized" : "initialized",
                    instance == null ? "No initialized CDI client exists; application use will initialize it."
                            : "An initialized CDI client exists."),
                    instance);
        } catch (RuntimeException exception) {
            // CDI/configuration error messages can contain connection details. Do not send them to the browser.
            return new Snapshot(summary(name, null, "unavailable", "The client state could not be inspected."), null);
        }
    }

    private static Map<String, Object> summary(String name, Boolean active, String state, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("active", active);
        result.put("state", state);
        result.put("detail", detail);
        return result;
    }

    private Map<String, Object> configured(String name) {
        Map<String, Object> result = new LinkedHashMap<>();
        var runtime = runtimeConfig.dataSources().get(name);
        var buildTime = buildTimeConfig.dataSources().get(name);
        if (runtime != null) {
            result.put("active", runtime.active().map(value -> value ? "true" : "false").orElse("auto"));
            result.put("dialect", runtime.dialect().orElse("auto"));
            result.put("mutationTransactionRequired", runtime.mutationTransactionRequired());
        }
        if (buildTime != null) {
            result.put("triggerType", buildTime.triggerType().name());
        }
        result.put("databaseValidationMode", runtimeConfig.databaseValidationMode().name());
        result.put("cacheRetryInterval", runtimeConfig.transactionCacheOperatorFixedDelay());
        return result;
    }

    private static JSqlClientImplementor nativeClient(Object instance) {
        if (instance.getClass().getName().equals("org.babyfish.jimmer.sql.kt.impl.KSqlClientImpl")) {
            instance = ((KSqlClient) instance).getJavaClient();
        }
        return instance.getClass().getName().equals("org.babyfish.jimmer.sql.JSqlClientImpl")
                ? (JSqlClientImplementor) instance
                : null;
    }

    private static Map<String, Object> actual(JSqlClientImplementor client) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dialect", implementationClass(client.getDialect()));
        result.put("triggerType", client.getTriggerType().name());
        result.put("mutationTransactionRequired", client.isMutationTransactionRequired());
        var connectionManager = client.getConnectionManager();
        result.put("connectionManager", implementationClass(connectionManager));
        result.put("transactionCapable", connectionManager instanceof TxConnectionManager);
        var operator = client.getCacheOperator();
        result.put("cacheOperator", implementationClass(operator));
        result.put("transactionCacheOperator", operator instanceof TransactionCacheOperator);
        Caches caches = client.getCaches();
        // This narrow implementation dependency avoids both user EntityManager callbacks and duplicate inherited properties.
        // Only Jimmer's exact registry implementation is inspected; custom implementations remain opaque.
        if (caches.getClass() != CachesImpl.class) {
            result.put("objectCacheCount", null);
            result.put("propertyCacheCount", null);
            return result;
        }
        CachesImpl registry = (CachesImpl) caches;
        int objects = 0;
        int properties = 0;
        for (ImmutableType type : registry.getObjectCacheMap().keySet()) {
            if (caches.getObjectCache(type) != null) {
                objects++;
            }
        }
        for (ImmutableProp prop : registry.getPropCacheMap().keySet()) {
            if (caches.getPropertyCache(prop) != null) {
                properties++;
            }
        }
        result.put("objectCacheCount", objects);
        result.put("propertyCacheCount", properties);
        return result;
    }

    private static String implementationClass(Object instance) {
        if (instance == null) {
            return "none";
        }
        return instance instanceof ClientProxy proxy ? proxy.arc_bean().getBeanClass().getName()
                : instance.getClass().getName();
    }

    private record Snapshot(Map<String, Object> summary, Object instance) {
    }
}
