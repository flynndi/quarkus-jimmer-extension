package io.quarkiverse.jimmer.deployment.transaction;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.runtime.transaction.QuarkusTransactionExecutor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ValidationPhaseBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.runtime.configuration.ConfigurationException;

final class JimmerTransactionProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerTransactionExecutor() {
        // Connection managers are constructed by Jimmer's factory and look up this boundary programmatically.
        return AdditionalBeanBuildItem.unremovableOf(QuarkusTransactionExecutor.class);
    }

    @BuildStep(onlyIf = Enabled.class)
    void checkTransactionsSupport(Capabilities capabilities,
            BuildProducer<ValidationPhaseBuildItem.ValidationErrorBuildItem> validationErrors) {
        // JTA is necessary for Jimmer
        if (capabilities.isMissing(Capability.TRANSACTIONS)) {
            validationErrors.produce(new ValidationPhaseBuildItem.ValidationErrorBuildItem(
                    new ConfigurationException("The Jimmer extension is only functional in a JTA environment.")));
        }
    }
}
