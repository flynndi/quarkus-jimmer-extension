package io.quarkiverse.jimmer.deployment.cache;

import java.util.List;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.ParameterizedType;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.JavaEnabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.KotlinEnabled;
import io.quarkiverse.jimmer.runtime.JimmerDataSourcesRecorder;
import io.quarkiverse.jimmer.runtime.JimmerTransactionCacheOperatorRecorder;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkiverse.jimmer.runtime.java.QuarkusJSqlClientContainer;
import io.quarkiverse.jimmer.runtime.kotlin.QuarkusKSqlClientContainer;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;

final class JimmerCacheProcessor {

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

    @BuildStep(onlyIf = JavaEnabled.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void setTransactionJCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            JimmerDataSourcesRecorder dataSourcesRecorder,
            JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();
            if (!buildTimeConfig.dataSources().get(dataSourceName).triggerType().equals(TriggerType.BINLOG_ONLY)) {
                SyntheticBeanBuildItem.ExtendedBeanConfigurator transactionCacheOperatorConfigurator = SyntheticBeanBuildItem
                        .configure(JimmerTransactionCacheOperatorRecorder.LazyTransactionCacheOperator.class)
                        .addType(TransactionCacheOperator.class)
                        .addType(org.babyfish.jimmer.sql.cache.CacheOperator.class)
                        .defaultBean()
                        .scope(Singleton.class)
                        .unremovable()
                        .setRuntimeInit()
                        .checkActive(dataSourcesRecorder.checkActiveSupplier(dataSourceName))
                        .addInjectionPoint(
                                ParameterizedType.create(InjectableInstance.class,
                                        ClassType.create(QuarkusJSqlClientContainer.class)),
                                DataSourceUtil.isDefault(dataSourceName)
                                        ? AnnotationInstance.builder(Default.class).build()
                                        : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build())
                        .createWith(recorder.transactionJCacheOperatorFunction(dataSourceName));

                if (DataSourceUtil.isDefault(dataSourceName)) {
                    transactionCacheOperatorConfigurator.addQualifier(Default.class);
                    transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                            .addValue("value", dataSourceName).done();

                    transactionCacheOperatorConfigurator.priority(Integer.MIN_VALUE);

                } else {
                    transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                            .addValue("value", dataSourceName).done();

                    transactionCacheOperatorConfigurator.priority(Integer.MIN_VALUE);
                }

                syntheticBeanBuildItemBuildProducer.produce(transactionCacheOperatorConfigurator.done());
            }
        }
    }

    @BuildStep(onlyIf = KotlinEnabled.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void setTransactionKCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            JimmerDataSourcesRecorder dataSourcesRecorder,
            JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
        if (jdbcDataSourceBuildItems.isEmpty()) {
            return;
        }

        for (JdbcDataSourceBuildItem jdbcDataSourceBuildItem : jdbcDataSourceBuildItems) {
            String dataSourceName = jdbcDataSourceBuildItem.getName();
            if (!buildTimeConfig.dataSources().get(dataSourceName).triggerType().equals(TriggerType.BINLOG_ONLY)) {
                SyntheticBeanBuildItem.ExtendedBeanConfigurator transactionCacheOperatorConfigurator = SyntheticBeanBuildItem
                        .configure(JimmerTransactionCacheOperatorRecorder.LazyTransactionCacheOperator.class)
                        .addType(TransactionCacheOperator.class)
                        .addType(org.babyfish.jimmer.sql.cache.CacheOperator.class)
                        .defaultBean()
                        .scope(Singleton.class)
                        .unremovable()
                        .setRuntimeInit()
                        .checkActive(dataSourcesRecorder.checkActiveSupplier(dataSourceName))
                        .addInjectionPoint(
                                ParameterizedType.create(InjectableInstance.class,
                                        ClassType.create(QuarkusKSqlClientContainer.class)),
                                DataSourceUtil.isDefault(dataSourceName)
                                        ? AnnotationInstance.builder(Default.class).build()
                                        : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build())
                        .createWith(recorder.transactionKCacheOperatorFunction(dataSourceName));

                if (DataSourceUtil.isDefault(dataSourceName)) {
                    transactionCacheOperatorConfigurator.addQualifier(Default.class);
                    transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                            .addValue("value", dataSourceName).done();

                    transactionCacheOperatorConfigurator.priority(Integer.MIN_VALUE);

                } else {
                    transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                            .addValue("value", dataSourceName).done();

                    transactionCacheOperatorConfigurator.priority(Integer.MIN_VALUE);
                }

                syntheticBeanBuildItemBuildProducer.produce(transactionCacheOperatorConfigurator.done());
            }
        }
    }
}
