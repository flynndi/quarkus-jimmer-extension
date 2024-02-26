package io.quarkiverse.jimmer.runtime.cfg;

import java.util.*;

import org.babyfish.jimmer.client.generator.ts.NullRenderMode;
import org.babyfish.jimmer.sql.EnumType;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.babyfish.jimmer.sql.runtime.DatabaseValidationMode;
import org.babyfish.jimmer.sql.runtime.IdOnlyTargetCheckingLevel;
import org.jetbrains.annotations.Nullable;

import io.quarkus.runtime.annotations.ConfigGroup;
import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(name = "jimmer", phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public class JimmerBuildTimeConfig {

    /**
     * jimmer.language
     */
    @ConfigItem(defaultValue = "java")
    public String language;

    /**
     * jimmer.showSql
     */
    @ConfigItem
    public boolean showSql;

    /**
     * jimmer.prettySql
     */
    @ConfigItem
    public boolean prettySql;

    /**
     * jimmer.inlineSqlVariables
     */
    @ConfigItem
    public boolean inlineSqlVariables;

    /**
     * jimmer.databaseValidation
     */
    @ConfigItem
    public DatabaseValidation databaseValidation;

    /**
     * jimmer.triggerType
     */
    @ConfigItem(defaultValue = "BINLOG_ONLY")
    public TriggerType triggerType;

    /**
     * jimmer.defaultDissociationActionCheckable
     */
    @ConfigItem(defaultValue = "true")
    public boolean defaultDissociationActionCheckable;

    /**
     * jimmer.idOnlyTargetCheckingLevel
     */
    @ConfigItem(defaultValue = "NONE")
    public IdOnlyTargetCheckingLevel idOnlyTargetCheckingLevel;

    /**
     * jimmer.transactionCacheOperatorFixedDelay
     */
    @ConfigItem(defaultValue = "5000")
    public OptionalInt transactionCacheOperatorFixedDelay;

    /**
     * jimmer.defaultEnumStrategy
     */
    @ConfigItem(defaultValue = "NAME")
    public EnumType.Strategy defaultEnumStrategy;

    /**
     * jimmer.defaultBatchSize
     */
    @ConfigItem
    public OptionalInt defaultBatchSize;

    /**
     * jimmer.defaultListBatchSize
     */
    @ConfigItem
    public OptionalInt defaultListBatchSize;

    /**
     * jimmer.offsetOptimizingThreshold
     */
    @ConfigItem
    public OptionalInt offsetOptimizingThreshold;

    /**
     * jimmer.isForeignKeyEnabledByDefault
     */
    @ConfigItem(defaultValue = "true")
    public boolean isForeignKeyEnabledByDefault;

    /**
     * jimmer.saveCommandPessimisticLock
     */
    @ConfigItem
    public boolean saveCommandPessimisticLock;

    /**
     * jimmer.executorContextPrefixes
     */
    @ConfigItem
    public Optional<List<String>> executorContextPrefixes = Optional.empty();

    /**
     * jimmer.microServiceName
     */
    @ConfigItem(defaultValue = "")
    public Optional<String> microServiceName;

    /**
     * jimmer.errorTranslator
     */
    @ConfigItem
    public Optional<ErrorTranslator> errorTranslator = Optional.empty();

    /**
     * jimmer.Client
     */
    @ConfigItem
    public Client client;

    @ConfigGroup
    public static class DatabaseValidation {

        /**
         * mode
         */
        @ConfigItem(defaultValue = "NONE")
        public DatabaseValidationMode mode;

        /**
         * catalog
         */
        @ConfigItem
        public Optional<String> catalog = Optional.empty();

        /**
         * schema
         */
        @ConfigItem
        public Optional<String> schema = Optional.empty();
    }

    @ConfigGroup
    public static class ErrorTranslator {

        /**
         * ErrorTranslatorBuildTimeConfig
         */
        @ConfigItem
        public boolean disabled;

        /**
         * httpStatus
         */
        @ConfigItem
        public int httpStatus;

        /**
         * debugInfoSupported
         */
        @ConfigItem
        public boolean debugInfoSupported;

        /**
         * debugInfoMaxStackTraceCount
         */
        @ConfigItem
        public int debugInfoMaxStackTraceCount;

        public ErrorTranslator(
                Boolean disabled,
                Integer httpStatus,
                Boolean debugInfoSupported,
                Integer debugInfoMaxStackTraceCount) {
            this.disabled = disabled != null ? disabled : false;
            this.httpStatus = httpStatus != null ? httpStatus : 500;
            this.debugInfoSupported = debugInfoSupported != null ? debugInfoSupported : false;
            this.debugInfoMaxStackTraceCount = debugInfoMaxStackTraceCount != null ? debugInfoMaxStackTraceCount
                    : Integer.MAX_VALUE;
        }
    }

    @ConfigGroup
    public static class Client {

        /**
         * jimmer.Client.TypeScript
         */
        @ConfigItem
        public TypeScript ts;

        /**
         * jimmer.Client.uriPrefix
         */
        @Nullable
        @ConfigItem
        public Optional<String> uriPrefix = Optional.empty();

        /**
         * jimmer.Client.controllerNullityChecked
         */
        @ConfigItem
        public boolean controllerNullityChecked;

        /**
         * jimmer.Client.openapi
         */
        @ConfigItem
        public Openapi openapi;
    }

    @ConfigGroup
    public static class TypeScript {

        /**
         * jimmer.Client.TypeScript.path
         */
        @ConfigItem
        @Nullable
        public Optional<String> path = Optional.empty();

        /**
         * jimmer.Client.TypeScript.apiName
         */
        @ConfigItem(defaultValue = "Api")
        @Nullable
        public Optional<String> apiName = Optional.empty();

        /**
         * jimmer.Client.TypeScript.indent
         */
        @ConfigItem(defaultValue = "4")
        public int indent;

        /**
         * jimmer.Client.TypeScript.mutable
         */
        @ConfigItem
        public boolean mutable;

        /**
         * jimmer.Client.TypeScript.nullRenderMode
         */
        @ConfigItem(defaultValue = "UNDEFINED")
        public NullRenderMode nullRenderMode;

        /**
         * jimmer.Client.TypeScript.isEnumTsStyle
         */
        @ConfigItem
        public boolean isEnumTsStyle;
    }

    @ConfigGroup
    public static class Openapi {

        /**
         * Openapi.path
         */
        @ConfigItem(defaultValue = "/openapi.yml")
        public Optional<String> path;

        /**
         * Openapi.uiPath
         */
        @ConfigItem(defaultValue = "/openapi.html")
        public Optional<String> uiPath;

        /**
         * Openapi.properties
         */
        @ConfigItem
        public Properties properties;
    }

    @ConfigGroup
    public static class Properties {

        /**
         * Properties.info
         */
        @ConfigItem
        public Info info;

        /**
         * Properties.servers
         */
        //        @ConfigItem
        //        public List<Server> servers;

        /**
         * Properties.securities
         */
        //        @ConfigItem
        //        public List<Map<String, List<String>>> securities;

        /**
         * Properties.components
         */
        @ConfigItem
        public Components components;
    }

    @ConfigGroup
    public static class Info {

        /**
         * Openapi.title
         */
        @ConfigItem
        public Optional<String> title;

        /**
         * Openapi.description
         */
        @ConfigItem
        public Optional<String> description;

        /**
         * Openapi.termsOfService
         */
        @ConfigItem
        public Optional<String> termsOfService;

        /**
         * Openapi.contact
         */
        @ConfigItem
        public Contact contact;

        /**
         * Openapi.license
         */
        @ConfigItem
        public License license;

        /**
         * Openapi.version
         */
        @ConfigItem
        public Optional<String> version;
    }

    @ConfigGroup
    public static class Contact {

        /**
         * Contact.name
         */
        @ConfigItem
        public Optional<String> name;

        /**
         * Contact.url
         */
        @ConfigItem
        public Optional<String> url;

        /**
         * Contact.email
         */
        @ConfigItem
        public Optional<String> email;
    }

    @ConfigGroup
    public static class License {

        /**
         * License.name
         */
        @ConfigItem
        public Optional<String> name;

        /**
         * License.identifier
         */
        @ConfigItem
        public Optional<String> identifier;
    }

    //    @ConfigGroup
    //    public static class Server {
    //
    //        /**
    //         * Server.url
    //         */
    //        @ConfigItem
    //        public Optional<String> url;
    //
    //        /**
    //         * Server.description
    //         */
    //        @ConfigItem
    //        public Optional<String> description;
    //
    //        /**
    //         * Server.variables
    //         */
    //        @ConfigItem
    //        public Map<String, Variable> variables;
    //
    //    }

    @ConfigGroup
    public static class Variable {

        /**
         * Variable.enums
         */
        @ConfigItem
        public Optional<List<String>> enums;

        /**
         * Variable.defaultValue
         */
        @ConfigItem
        public Optional<String> defaultValue;

        /**
         * Variable.description
         */
        @ConfigItem
        public Optional<String> description;
    }

    @ConfigGroup
    public static class Components {

        /**
         * Components.securitySchemes
         */
        @ConfigItem
        public Map<String, SecurityScheme> securitySchemes;
    }

    @ConfigGroup
    public static class SecurityScheme {

        /**
         * SecurityScheme.type
         */
        @ConfigItem
        public Optional<String> type;

        /**
         * SecurityScheme.description
         */
        @ConfigItem
        public Optional<String> description;

        /**
         * SecurityScheme.name
         */
        @ConfigItem
        public Optional<String> name;

        /**
         * SecurityScheme.in
         */
        @ConfigItem(defaultValue = "HEADER")
        public In in;

        /**
         * SecurityScheme.scheme
         */
        @ConfigItem
        public Optional<String> scheme;

        /**
         * SecurityScheme.bearerFormat
         */
        @ConfigItem
        public Optional<String> bearerFormat;

        /**
         * SecurityScheme.flows
         */
        @ConfigItem
        public Flows flows;

        /**
         * SecurityScheme.openIdConnectUrl
         */
        @ConfigItem
        public Optional<String> openIdConnectUrl;
    }

    public enum In {
        QUERY,
        HEADER,
        COOKIE
    }

    @ConfigGroup
    public static class Flows {

        /**
         * Flows.implicit
         */
        @ConfigItem
        public Flow implicit;

        /**
         * Flows.password
         */
        @ConfigItem
        public Flow password;

        /**
         * Flows.clientCredentials
         */
        @ConfigItem
        public Flow clientCredentials;

        /**
         * Flows.authorizationCode
         */
        @ConfigItem
        public Flow authorizationCode;
    }

    @ConfigGroup
    public static class Flow {

        /**
         * Flow.authorizationUrl
         */
        @ConfigItem
        public Optional<String> authorizationUrl;

        /**
         * Flow.tokenUrl
         */
        @ConfigItem
        public Optional<String> tokenUrl;

        /**
         * Flow.refreshUrl
         */
        @ConfigItem
        public Optional<String> refreshUrl;

        /**
         * Flow.scopes
         */
        @ConfigItem
        public Map<String, String> scopes;
    }
}
