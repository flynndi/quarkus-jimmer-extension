package io.quarkiverse.jimmer.deployment;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;

/** Shared build-time decisions for the built-in transports, independent of custom exchange beans. */
final class JimmerMicroserviceSupport {

    private JimmerMicroserviceSupport() {
    }

    static boolean enabled(JimmerBuildTimeConfig config) {
        return config.enable() && config.microServiceName().filter(name -> !name.isBlank()).isPresent();
    }

    static boolean httpExchangeAvailable(Capabilities capabilities) {
        return capabilities.isPresent(Capability.REST_CLIENT_REACTIVE) && capabilities.isPresent(Capability.JACKSON);
    }

    static boolean exporterAvailable(Capabilities capabilities) {
        return capabilities.isPresent(Capability.VERTX_HTTP) && capabilities.isPresent(Capability.JACKSON);
    }

    static boolean httpExchangeEnabled(JimmerBuildTimeConfig config, Capabilities capabilities) {
        return enabled(config) && httpExchangeAvailable(capabilities);
    }

    static boolean exporterEnabled(JimmerBuildTimeConfig config, Capabilities capabilities) {
        return enabled(config) && exporterAvailable(capabilities);
    }
}
