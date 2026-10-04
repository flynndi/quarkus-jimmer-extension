package io.quarkiverse.jimmer.runtime.cfg;

import java.util.Map;
import java.util.Optional;

import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.jimmer")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface JimmerBuildTimeConfig {

    /**
     * jimmer.enable
     */
    @WithDefault("true")
    boolean enable();

    /**
     * jimmer.language
     */
    @WithDefault("java")
    String language();

    /**
     * The Jimmer microservice identity, independent of the transport used to communicate with other services.
     */
    Optional<String> microServiceName();

    @ConfigDocMapKey("datasource-name")
    @WithParentName
    @WithDefaults
    @WithUnnamedKey(DataSourceUtil.DEFAULT_DATASOURCE_NAME)
    Map<String, JimmerDataSourceBuildTimeConfig> dataSources();

    /**
     * jimmer.errorTranslator
     */
    Optional<ErrorTranslator> errorTranslator();

    @ConfigGroup
    interface ErrorTranslator {

        /**
         * Disables Jimmer's REST exception mappers. Set to false to enable them when using quarkus-rest-jackson.
         */
        @WithDefault("true")
        boolean disabled();

        /**
         * httpStatus
         */
        @WithDefault("500")
        int httpStatus();

        /**
         * debugInfoSupported
         */
        @WithDefault("false")
        boolean debugInfoSupported();

        /**
         * debugInfoMaxStackTraceCount
         */
        @WithDefault("2147483647")
        int debugInfoMaxStackTraceCount();
    }

    /**
     * Client generation and its optional HTTP endpoints.
     */
    JimmerClientConfig client();
}
