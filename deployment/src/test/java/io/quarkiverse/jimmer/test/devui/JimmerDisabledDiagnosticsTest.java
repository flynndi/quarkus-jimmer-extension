package io.quarkiverse.jimmer.test.devui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.quarkiverse.jimmer.runtime.devui.JimmerDevUIService;
import io.smallrye.config.SmallRyeConfigBuilder;

class JimmerDisabledDiagnosticsTest {
    @Test
    void disabledDiagnosticsNeedNoArcContainer() {
        var config = new SmallRyeConfigBuilder().addDefaultInterceptors()
                .withMapping(JimmerBuildTimeConfig.class).withMapping(JimmerRuntimeConfig.class)
                .withDefaultValue("quarkus.jimmer.enable", "false").build();
        var diagnostics = new JimmerDevUIService(config.getConfigMapping(JimmerBuildTimeConfig.class),
                config.getConfigMapping(JimmerRuntimeConfig.class));
        assertEquals(false, diagnostics.getClients().get("enabled"));
        assertEquals(List.of(), diagnostics.getClients().get("clients"));
        var selected = diagnostics.getClient("<default>");
        assertEquals("unavailable", selected.get("state"));
        assertNull(selected.get("actual"));
    }
}
