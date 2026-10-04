package io.quarkiverse.jimmer.runtime.cfg;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;

/** Client generation and endpoint settings, independent of the optional generation library. */
@ConfigGroup
public interface JimmerClientConfig {

    /**
     * TypeScript client generation.
     */
    TypeScript ts();

    /**
     * URI prefix applied to the generated operation paths.
     */
    Optional<String> uriPrefix();

    /**
     * OpenAPI document generation.
     */
    JimmerOpenApiConfig openapi();

    @ConfigGroup
    interface TypeScript {

        /**
         * Path for the generated TypeScript ZIP. No endpoint is exposed unless this is configured.
         */
        Optional<String> path();

        /**
         * Name of the generated TypeScript API entry point.
         */
        @WithDefault("Api")
        String apiName();

        /**
         * Indentation width of generated TypeScript sources.
         */
        @WithDefault("4")
        int indent();

        /**
         * Whether generated TypeScript properties are mutable.
         */
        @WithDefault("false")
        boolean mutable();

        /**
         * How nullable TypeScript properties represent absent values.
         */
        @WithDefault("UNDEFINED")
        NullRenderMode nullRenderMode();

        /**
         * Whether TypeScript enums are generated instead of string unions.
         */
        @WithDefault("false")
        boolean isEnumTsStyle();

        /**
         * How nullable values are represented in generated TypeScript clients.
         */
        enum NullRenderMode {
            UNDEFINED,
            NULL_OR_UNDEFINED
        }
    }
}
