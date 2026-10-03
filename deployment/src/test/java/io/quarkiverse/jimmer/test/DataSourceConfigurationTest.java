package io.quarkiverse.jimmer.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.babyfish.jimmer.sql.runtime.DatabaseValidationMode;
import org.junit.jupiter.api.Test;

import io.quarkiverse.jimmer.runtime.cfg.JimmerRuntimeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.common.MapBackedConfigSource;

class DataSourceConfigurationTest {

    @Test
    void validationModeUsesNewKeyAndPreservesLegacyFallback() {
        assertEquals(DatabaseValidationMode.NONE, config(Map.of()).databaseValidationMode());
        assertEquals(DatabaseValidationMode.ERROR,
                config(Map.of("quarkus.jimmer.database-validation-mode", "ERROR")).databaseValidationMode());
        assertEquals(DatabaseValidationMode.ERROR,
                config(Map.of("quarkus.jimmer.database-validation.mode", "ERROR")).databaseValidationMode());
        assertEquals(DatabaseValidationMode.NONE,
                config(Map.of("quarkus.jimmer.database-validation-mode", "NONE",
                        "quarkus.jimmer.database-validation.mode", "ERROR")).databaseValidationMode());
    }

    private JimmerRuntimeConfig config(Map<String, String> values) {
        return new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .withMapping(JimmerRuntimeConfig.class)
                .withSources(new MapBackedConfigSource("test", values) {
                })
                .build()
                .getConfigMapping(JimmerRuntimeConfig.class);
    }
}
