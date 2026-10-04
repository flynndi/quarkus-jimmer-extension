package io.quarkiverse.jimmer.deployment;

import java.util.List;

import org.babyfish.jimmer.sql.event.TriggerType;

import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;

final class JimmerSchedulerProcessor {

    // Keep optional scheduler types out of the always-loaded processor's signatures and class literals.
    private static final String RETRY_JOB = "io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorRetryJob";

    @BuildStep
    JimmerCacheRetryBuildItem registerCacheFlushing(JimmerBuildTimeConfig config, Capabilities capabilities,
            List<JdbcDataSourceBuildItem> dataSources,
            BuildProducer<AdditionalBeanBuildItem> beans, BuildProducer<ExcludedTypeBuildItem> excluded) {
        boolean transactionCache = config.enable() && dataSources.stream()
                .anyMatch(source -> config.dataSources().get(source.getName()).triggerType() != TriggerType.BINLOG_ONLY);
        if (transactionCache) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(TransactionCacheOperatorFlusher.class));
        } else {
            // The scoped observer would otherwise be discovered even when no datasource uses transaction caches.
            excluded.produce(new ExcludedTypeBuildItem(TransactionCacheOperatorFlusher.class.getName()));
        }
        boolean schedulerAvailable = capabilities.isPresent(Capability.SCHEDULER);
        boolean retryJobRegistered = transactionCache && schedulerAvailable;
        if (retryJobRegistered) {
            beans.produce(AdditionalBeanBuildItem.builder().addBeanClass(RETRY_JOB).setUnremovable().build());
        } else {
            // @Scheduled can implicitly add a bean scope. Not producing AdditionalBeanBuildItem is insufficient.
            excluded.produce(new ExcludedTypeBuildItem(RETRY_JOB));
        }
        return new JimmerCacheRetryBuildItem(schedulerAvailable, retryJobRegistered);
    }
}
