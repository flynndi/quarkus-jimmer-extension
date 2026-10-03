package io.quarkiverse.jimmer.deployment;

import io.quarkiverse.jimmer.runtime.transaction.QuarkusTransactionExecutor;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;

final class JimmerTransactionProcessor {

    @BuildStep
    AdditionalBeanBuildItem registerTransactionExecutor() {
        // Connection managers are constructed by Jimmer's factory and look up this boundary programmatically.
        return AdditionalBeanBuildItem.unremovableOf(QuarkusTransactionExecutor.class);
    }
}
