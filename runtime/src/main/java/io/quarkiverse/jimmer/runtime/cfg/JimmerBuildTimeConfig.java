package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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

        public boolean isDisabled() {
            return disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public void setHttpStatus(int httpStatus) {
            this.httpStatus = httpStatus;
        }

        public boolean isDebugInfoSupported() {
            return debugInfoSupported;
        }

        public void setDebugInfoSupported(boolean debugInfoSupported) {
            this.debugInfoSupported = debugInfoSupported;
        }

        public int getDebugInfoMaxStackTraceCount() {
            return debugInfoMaxStackTraceCount;
        }

        public void setDebugInfoMaxStackTraceCount(int debugInfoMaxStackTraceCount) {
            this.debugInfoMaxStackTraceCount = debugInfoMaxStackTraceCount;
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
}
