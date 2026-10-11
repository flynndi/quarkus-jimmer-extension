package io.quarkiverse.jimmer.deployment.cfg;

import java.util.List;
import java.util.Set;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.runtime.JimmerConfigRecorder;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.configuration.ConfigurationException;

final class JimmerConfigProcessor {

    @BuildStep
    ValidationErrorBuildItem validateBuildTime(JimmerBuildTimeConfig config) {
        try {
            JimmerConfigValidator.validateBuildTime(config);
            if (config.enable() && "kotlin".equalsIgnoreCase(config.language())
                    && !QuarkusClassLoader.isClassPresentAtRuntime("org.babyfish.jimmer.sql.kt.KSqlClient")) {
                throw new ConfigurationException(
                        "quarkus.jimmer.language=kotlin requires org.babyfish.jimmer:jimmer-sql-kotlin. "
                                + "Add it using the same version as the extension's Jimmer dependencies.",
                        Set.of("quarkus.jimmer.language"));
            }
            return null;
        } catch (ConfigurationException ex) {
            return new ValidationErrorBuildItem(ex);
        }
    }

    @BuildStep(onlyIf = Enabled.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    void validateRuntime(JimmerConfigRecorder recorder, List<JdbcDataSourceBuildItem> dataSources) {
        recorder.validateActiveDataSources(dataSources.stream().map(JdbcDataSourceBuildItem::getName).sorted().toList());
    }
}
