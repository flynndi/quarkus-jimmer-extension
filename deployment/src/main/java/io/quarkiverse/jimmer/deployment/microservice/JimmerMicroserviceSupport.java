package io.quarkiverse.jimmer.deployment.microservice;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;

/** Shared build-time decisions for the built-in transports, independent of custom exchange beans. */
public final class JimmerMicroserviceSupport {

    private JimmerMicroserviceSupport() {
    }

    static boolean enabled(JimmerBuildTimeConfig config) {
        return config.enable() && config.microServiceName().filter(name -> !name.isBlank()).isPresent();
    }

    public static boolean httpExchangeAvailable(Capabilities capabilities) {
        return capabilities.isPresent(Capability.REST_CLIENT_REACTIVE) && capabilities.isPresent(Capability.JACKSON);
    }

    public static boolean exporterAvailable(Capabilities capabilities) {
        return capabilities.isPresent(Capability.VERTX_HTTP) && capabilities.isPresent(Capability.JACKSON);
    }

    public static boolean httpExchangeEnabled(JimmerBuildTimeConfig config, Capabilities capabilities) {
        return enabled(config) && httpExchangeAvailable(capabilities);
    }

    public static boolean exporterEnabled(JimmerBuildTimeConfig config, Capabilities capabilities) {
        return enabled(config) && exporterAvailable(capabilities);
    }
}
