package io.quarkiverse.jimmer.runtime.cfg;

import org.babyfish.jimmer.sql.event.TriggerType;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface JimmerDataSourceBuildTimeConfig {

    /**
     * jimmer.triggerType
     */
    @WithDefault("BINLOG_ONLY")
    TriggerType triggerType();
}
