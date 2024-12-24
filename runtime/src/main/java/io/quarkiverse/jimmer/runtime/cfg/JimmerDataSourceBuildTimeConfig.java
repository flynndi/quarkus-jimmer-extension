package io.quarkiverse.jimmer.runtime.cfg;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;

@ConfigGroup
public interface JimmerDataSourceBuildTimeConfig {

    /**
     * jimmer.dialect
     */
    Optional<String> dialect();
}
