package io.quarkiverse.jimmer.runtime.cfg;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface JimmerDataSourceRuntimeConfig {

    /**
     * Flag to activate/deactivate Jimmer for a specific datasource at runtime.
     */
    @ConfigDocDefault("'true' if the datasource is active; 'false' otherwise")
    Optional<Boolean> active();

    /**
     * jimmer.dialect
     */
    Optional<String> dialect();
}
