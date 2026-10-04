package io.quarkiverse.jimmer.deployment.cfg;

import java.util.List;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.runtime.JimmerConfigRecorder;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.cfg.JimmerConfigValidator;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem.ValidationErrorBuildItem;
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
