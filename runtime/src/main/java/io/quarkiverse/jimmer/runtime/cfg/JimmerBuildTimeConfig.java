package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.babyfish.jimmer.client.generator.openapi.OpenApiProperties;
import org.babyfish.jimmer.client.generator.ts.NullRenderMode;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.Converter;

import io.quarkiverse.jimmer.runtime.util.StringUtils;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.*;

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
     * jimmer.microServiceName
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
         * ErrorTranslatorBuildTimeConfig
         */
        @WithDefault("false")
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
        @WithConverter(OpenapiRefPathConverter.class)
        Optional<String> refPath();

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

    class OpenapiRefPathConverter implements Converter<String> {

        public OpenapiRefPathConverter() {
        }

        @Override
        public String convert(String s) {
            final ClassLoader cl = OpenapiRefPathConverter.class.getClassLoader();
            final Config config = ConfigProviderResolver.instance().getConfig(cl);
            if (!StringUtils.hasText(s)) {
                if (config.getOptionalValue("quarkus.jimmer.client.openapi.path", String.class).isEmpty()) {
                    return null;
                } else {
                    return config.getOptionalValue("quarkus.jimmer.client.openapi.path", String.class).get();
                }
            } else {
                return s;
            }
        }
    }
}
