package io.quarkiverse.jimmer.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.common.MapBackedConfigSource;

class JimmerMicroserviceProcessorTest {

    @Test
    void automaticTransportsFollowTheirOwnCapabilities() {
        var config = config(Map.of("quarkus.jimmer.micro-service-name", "inventory"));
        Capabilities outbound = new Capabilities(Set.of(Capability.REST_CLIENT_REACTIVE, Capability.JACKSON));
        assertTrue(JimmerMicroserviceSupport.httpExchangeEnabled(config, outbound));
        assertFalse(JimmerMicroserviceSupport.exporterEnabled(config, outbound));
        Capabilities inbound = new Capabilities(Set.of(Capability.VERTX_HTTP, Capability.JACKSON));
        assertFalse(JimmerMicroserviceSupport.httpExchangeEnabled(config, inbound));
        assertTrue(JimmerMicroserviceSupport.exporterEnabled(config, inbound));
        assertFalse(JimmerMicroserviceSupport.httpExchangeEnabled(config, new Capabilities(Set.of())));
        assertFalse(JimmerMicroserviceSupport.exporterEnabled(config, new Capabilities(Set.of())));
    }

    @Test
    void missingTransportCapabilitiesDoNotRegisterBeansIndexOrReflection() {
        var config = config(Map.of("quarkus.jimmer.micro-service-name", "inventory"));
        var capabilities = new Capabilities(Set.of(Capability.JACKSON));
        List<AdditionalBeanBuildItem> beans = new ArrayList<>();
        List<AdditionalIndexedClassesBuildItem> indexed = new ArrayList<>();
        List<ExcludedTypeBuildItem> excluded = new ArrayList<>();
        List<ReflectiveClassBuildItem> reflection = new ArrayList<>();
        new JimmerMicroserviceProcessor().registerBeans(config, capabilities,
                beans::add, indexed::add, excluded::add, reflection::add);
        assertTrue(beans.isEmpty());
        assertTrue(indexed.isEmpty());
        assertTrue(reflection.isEmpty());
        assertEquals(3, excluded.size());
    }

    private JimmerBuildTimeConfig config(Map<String, String> values) {
        return new SmallRyeConfigBuilder().addDefaultInterceptors().withMapping(JimmerBuildTimeConfig.class)
                .withSources(new MapBackedConfigSource("test", values) {
                }).build().getConfigMapping(JimmerBuildTimeConfig.class);
    }
}
