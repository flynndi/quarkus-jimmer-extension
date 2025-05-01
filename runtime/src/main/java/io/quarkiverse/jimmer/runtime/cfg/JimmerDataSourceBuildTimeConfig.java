package io.quarkiverse.jimmer.runtime.cfg;

import org.babyfish.jimmer.sql.event.TriggerType;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

@ConfigGroup
public interface JimmerDataSourceBuildTimeConfig {

    /**
     * jimmer.showSql
     */
    @WithDefault("false")
    boolean showSql();

    /**
     * jimmer.prettySql
     */
    @WithDefault("false")
    boolean prettySql();

    /**
     * jimmer.inlineSqlVariables
     */
    @WithDefault("false")
    boolean inlineSqlVariables();

    /**
     * jimmer.triggerType
     */
    @WithDefault("BINLOG_ONLY")
    TriggerType triggerType();
}
