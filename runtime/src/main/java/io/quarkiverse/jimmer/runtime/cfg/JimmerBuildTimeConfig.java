package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.babyfish.jimmer.client.generator.openapi.OpenApiProperties;
import org.babyfish.jimmer.client.generator.ts.NullRenderMode;
import org.babyfish.jimmer.sql.event.TriggerType;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

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

    /**
     * jimmer.microServiceName
     */
    Optional<String> microServiceName();

    /**
     * jimmer.errorTranslator
     */
    Optional<JimmerRuntimeConfig.ErrorTranslator> errorTranslator();

    /**
     * jimmer.Client
     */
    Client client();

    @ConfigGroup
    interface Client {

        /**
         * jimmer.Client.TypeScript
         */
        TypeScript ts();

        /**
         * jimmer.Client.uriPrefix
         */
        Optional<String> uriPrefix();

        /**
         * jimmer.Client.controllerNullityChecked
         */
        @WithDefault("false")
        boolean controllerNullityChecked();

        /**
         * jimmer.Client.openapi
         */
        Openapi openapi();
    }

    @ConfigGroup
    interface TypeScript {

        /**
         * jimmer.Client.TypeScript.path
         */
        Optional<String> path();

        /**
         * jimmer.Client.TypeScript.apiName
         */
        @WithDefault("Api")
        String apiName();

        /**
         * jimmer.Client.TypeScript.indent
         */
        @WithDefault("4")
        int indent();

        /**
         * jimmer.Client.TypeScript.mutable
         */
        @WithDefault("false")
        boolean mutable();

        /**
         * jimmer.Client.TypeScript.nullRenderMode
         */
        @WithDefault("UNDEFINED")
        NullRenderMode nullRenderMode();

        /**
         * jimmer.Client.TypeScript.isEnumTsStyle
         */
        @WithDefault("false")
        boolean isEnumTsStyle();
    }

    @ConfigGroup
    interface Openapi {

        /**
         * Openapi.path
         */
        @WithDefault("/openapi.yml")
        String path();

        /**
         * Openapi.uiPath
         */
        @WithDefault("/openapi.html")
        String uiPath();

        /**
         * Openapi.refPath
         */
        @WithDefault("/openapi.yml")
        String refPath();

        /**
         * Openapi.properties
         */
        Properties properties();
    }

    @ConfigGroup
    interface Properties {

        /**
         * Properties.info
         */
        Info info();

        /**
         * Properties.servers
         */
        Optional<List<Server>> servers();

        /**
         * Properties.securities
         */
        Optional<List<Map<String, List<String>>>> securities();

        /**
         * Properties.components
         */
        Components components();
    }

    @ConfigGroup
    interface Info {

        /**
         * Openapi.title
         */
        Optional<String> title();

        /**
         * Openapi.description
         */
        Optional<String> description();

        /**
         * Openapi.termsOfService
         */
        Optional<String> termsOfService();

        /**
         * Openapi.contact
         */
        Contact contact();

        /**
         * Openapi.license
         */
        License license();

        /**
         * Openapi.version
         */
        Optional<String> version();
    }

    @ConfigGroup
    interface Contact {

        /**
         * Contact.name
         */
        Optional<String> name();

        /**
         * Contact.url
         */
        Optional<String> url();

        /**
         * Contact.email
         */
        Optional<String> email();
    }

    @ConfigGroup
    interface License {

        /**
         * License.name
         */
        Optional<String> name();

        /**
         * License.identifier
         */
        Optional<String> identifier();
    }

    @ConfigGroup
    interface Server {

        /**
         * Server.url
         */
        @WithName("url")
        Optional<String> url();

        /**
         * Server.description
         */
        @WithName("description")
        Optional<String> description();

        /**
         * Server.variables
         */
        @ConfigDocMapKey("variable")
        @WithName("variables")
        Map<String, Variable> variables();
    }

    @ConfigGroup
    interface Variable {

        /**
         * Variable.enums
         */
        @WithName("enums")
        Optional<List<String>> enums();

        /**
         * Variable.defaultValue
         */
        @WithName("defaultValue")
        Optional<String> defaultValue();

        /**
         * Variable.description
         */
        @WithName("description")
        Optional<String> description();
    }

    @ConfigGroup
    interface Components {

        /**
         * Components.securitySchemes
         */
        @ConfigDocMapKey("scheme")
        @WithName("securitySchemes")
        Map<String, SecurityScheme> securitySchemes();
    }

    @ConfigGroup
    interface SecurityScheme {

        /**
         * SecurityScheme.type
         */
        Optional<String> type();

        /**
         * SecurityScheme.description
         */
        Optional<String> description();

        /**
         * SecurityScheme.name
         */
        Optional<String> name();

        /**
         * SecurityScheme.in
         */
        @WithDefault("HEADER")
        OpenApiProperties.In in();

        /**
         * SecurityScheme.scheme
         */
        Optional<String> scheme();

        /**
         * SecurityScheme.bearerFormat
         */
        Optional<String> bearerFormat();

        /**
         * SecurityScheme.flows
         */
        Flows flows();

        /**
         * SecurityScheme.openIdConnectUrl
         */
        Optional<String> openIdConnectUrl();
    }

    @ConfigGroup
    interface Flows {

        /**
         * Flows.implicit
         */
        @WithName("implicit")
        Optional<Flow> implicit();

        /**
         * Flows.password
         */
        @WithName("password")
        Optional<Flow> password();

        /**
         * Flows.clientCredentials
         */
        @WithName("clientCredentials")
        Optional<Flow> clientCredentials();

        /**
         * Flows.authorizationCode
         */
        @WithName("authorizationCode")
        Optional<Flow> authorizationCode();
    }

    @ConfigGroup
    interface Flow {

        /**
         * Flow.authorizationUrl
         */
        @WithName("authorizationUrl")
        Optional<String> authorizationUrl();

        /**
         * Flow.tokenUrl
         */
        @WithName("tokenUrl")
        Optional<String> tokenUrl();

        /**
         * Flow.refreshUrl
         */
        @WithName("refreshUrl")
        Optional<String> refreshUrl();

        /**
         * Flow.scopes
         */
        @ConfigDocMapKey("flowScopes")
        @WithName("scopes")
        Map<String, String> scopes();
    }
}
