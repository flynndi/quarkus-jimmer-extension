package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigGroup;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/** OpenAPI configuration mapped by Quarkus and converted only inside the generator integration. */
@ConfigGroup
public interface JimmerOpenApiConfig {

    /**
     * Path for the generated OpenAPI specification. No endpoint is exposed unless this is configured.
     */
    Optional<String> path();

    /**
     * Properties of the generated OpenAPI document.
     */
    Properties properties();

    @ConfigGroup
    interface Properties {

        /**
         * General information about the API.
         */
        Info info();

        /**
         * Server URLs available to API consumers.
         */
        Optional<List<Server>> servers();

        /**
         * Alternative security requirements; schemes in one map must be satisfied together.
         */
        Optional<List<Map<String, List<String>>>> securities();

        /**
         * Reusable security definitions.
         */
        Components components();
    }

    @ConfigGroup
    interface Info {

        /**
         * API title. Generation uses Jimmer's default title when this is absent.
         */
        Optional<String> title();

        /**
         * API description. Generation uses Jimmer's default description when this is absent.
         */
        Optional<String> description();

        /**
         * URL of the terms of service.
         */
        Optional<String> termsOfService();

        /**
         * Contact information for the API.
         */
        Contact contact();

        /**
         * License information for the API.
         */
        License license();

        /**
         * API version. Generation defaults to 1.0.0 when this is absent.
         */
        Optional<String> version();
    }

    @ConfigGroup
    interface Contact {

        /**
         * Name of the API contact.
         */
        Optional<String> name();

        /**
         * URL for the API contact.
         */
        Optional<String> url();

        /**
         * Email address of the API contact.
         */
        Optional<String> email();
    }

    @ConfigGroup
    interface License {

        /**
         * Name of the API license.
         */
        Optional<String> name();

        /**
         * License identifier passed to the Jimmer generator.
         */
        Optional<String> identifier();
    }

    @ConfigGroup
    interface Server {

        /**
         * Server URL, optionally containing named variables.
         */
        Optional<String> url();

        /**
         * Description of the server.
         */
        Optional<String> description();

        /**
         * Named variables used by the server URL.
         */
        @ConfigDocMapKey("variable")
        Map<String, Variable> variables();
    }

    @ConfigGroup
    interface Variable {

        /**
         * Allowed values of this server variable.
         */
        Optional<List<String>> enums();

        /**
         * Default value of this server variable.
         */
        @WithName("defaultValue")
        Optional<String> defaultValue();

        /**
         * Description of this server variable.
         */
        Optional<String> description();
    }

    @ConfigGroup
    interface Components {

        /**
         * Security schemes referenced by document security requirements.
         */
        @ConfigDocMapKey("scheme")
        @WithName("securitySchemes")
        Map<String, SecurityScheme> securitySchemes();
    }

    @ConfigGroup
    interface SecurityScheme {

        /**
         * Security scheme type: apiKey, http, oauth2, or openIdConnect.
         */
        Optional<String> type();

        /**
         * Description of the security scheme.
         */
        Optional<String> description();

        /**
         * Name of the API key parameter; required for an apiKey scheme.
         */
        Optional<String> name();

        /**
         * Location of an API key in the request.
         */
        @WithDefault("HEADER")
        In in();

        /**
         * HTTP authentication scheme; required for an http scheme.
         */
        Optional<String> scheme();

        /**
         * Hint describing the format of a bearer token.
         */
        Optional<String> bearerFormat();

        /**
         * OAuth 2 flows supported by this scheme.
         */
        Flows flows();

        /**
         * OpenID Connect discovery URL; required for an openIdConnect scheme.
         */
        Optional<String> openIdConnectUrl();

        /**
         * The location of an API key in a request.
         */
        enum In {
            QUERY,
            HEADER,
            COOKIE
        }
    }

    @ConfigGroup
    interface Flows {

        /**
         * OAuth implicit flow.
         */
        Optional<Flow> implicit();

        /**
         * OAuth password flow.
         */
        Optional<Flow> password();

        /**
         * OAuth client credentials flow.
         */
        @WithName("clientCredentials")
        Optional<Flow> clientCredentials();

        /**
         * OAuth authorization code flow.
         */
        @WithName("authorizationCode")
        Optional<Flow> authorizationCode();
    }

    @ConfigGroup
    interface Flow {

        /**
         * Authorization URL; required for implicit and authorization code flows.
         */
        @WithName("authorizationUrl")
        Optional<String> authorizationUrl();

        /**
         * Token URL; required for password, client credentials, and authorization code flows.
         */
        @WithName("tokenUrl")
        Optional<String> tokenUrl();

        /**
         * URL used to obtain refresh tokens.
         */
        @WithName("refreshUrl")
        Optional<String> refreshUrl();

        /**
         * Available OAuth scopes and their descriptions.
         */
        @ConfigDocMapKey("flowScopes")
        Map<String, String> scopes();
    }
}
