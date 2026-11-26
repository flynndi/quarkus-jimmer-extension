package io.quarkiverse.jimmer.deployment.cache;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.PropCacheInvalidator;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.ParameterizedType;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Jackson2Present;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.JavaEnabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.KotlinEnabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.RedissonKryoPresent;
import io.quarkiverse.jimmer.runtime.JimmerConfigRecorder;
import io.quarkiverse.jimmer.runtime.JimmerTransactionCacheOperatorRecorder;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig;
import io.quarkus.agroal.DataSource;
import io.quarkus.agroal.spi.JdbcDataSourceBuildItem;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.ExcludedTypeBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.BytecodeTransformerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.RemovedResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveFieldBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveMethodBuildItem;
import io.quarkus.deployment.pkg.steps.NativeOrNativeSourcesBuild;
import io.quarkus.gizmo.ClassTransformer;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.maven.dependency.ArtifactKey;

final class JimmerCacheProcessor {

    // Keep optional scheduler types out of the always-loaded processor's signatures and class literals.
    private static final String RETRY_JOB = "io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorRetryJob";

    @BuildStep(onlyIf = NativeOrNativeSourcesBuild.class, onlyIfNot = Jackson2Present.class)
    void excludeUnavailableRedissonCodec(BuildProducer<RemovedResourceBuildItem> removed) {
        // Redisson registers this codec for native reflection even when Jackson 2 is absent.
        if (QuarkusClassLoader.isClassPresentAtRuntime("org.redisson.codec.JsonJacksonCodec")) {
            removed.produce(new RemovedResourceBuildItem(ArtifactKey.of("org.redisson", "redisson"),
                    Set.of("org/redisson/codec/JsonJacksonCodec.class")));
        }
    }

    @BuildStep(onlyIf = { NativeOrNativeSourcesBuild.class, RedissonKryoPresent.class })
    void supportKryoInvalidationMessages(CombinedIndexBuildItem index,
            BuildProducer<BytecodeTransformerBuildItem> transformers,
            BuildProducer<ReflectiveClassBuildItem> types, BuildProducer<ReflectiveMethodBuildItem> methods,
            BuildProducer<ReflectiveFieldBuildItem> fields) {
        String name = "org.babyfish.jimmer.sql.cache.redisson.InvalidateMessage";
        var message = index.getComputingIndex().getClassByName(DotName.createSimple(name));
        if (message == null) {
            return;
        }
        if (!message.hasNoArgsConstructor()) {
            // Kryo5Codec can invoke this constructor in native images, avoiding its Objenesis fallback.
            transformers.produce(new BytecodeTransformerBuildItem(name, (className, visitor) -> {
                var transformer = new ClassTransformer(className);
                var constructor = transformer.addMethod(MethodDescriptor.ofConstructor(className));
                constructor.setModifiers(Modifier.PRIVATE);
                constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(message.superName().toString()),
                        constructor.getThis());
                constructor.returnVoid();
                return transformer.applyTo(visitor);
            }));
        }
        // Headless/manual clients can lack Jackson's hierarchy registration; Quarkus merges overlapping members.
        String reason = "Redisson Kryo cache invalidation messages";
        // Cache.delete uses Collections.singleton, and deleteAll preserves an application's Set.of ids.
        // Kryo resolves the set and UUID classes by name; their serializers construct them directly.
        types.produce(ReflectiveClassBuildItem.builder(Collections.singleton(null).getClass(),
                Set.of(1).getClass(), Set.of().getClass(), UUID.class)
                .constructors(false).reason(reason).build());
        // AbstractTrackingProducerBinder and TransactionCacheOperator collect batched ids into a LinkedHashSet.
        methods.produce(new ReflectiveMethodBuildItem(reason, LinkedHashSet.class.getName(), "<init>"));
        methods.produce(new ReflectiveMethodBuildItem(reason, name, "<init>"));
        for (var field : message.fields()) {
            if (!Modifier.isStatic(field.flags()) && !Modifier.isTransient(field.flags())) {
                fields.produce(new ReflectiveFieldBuildItem(reason, field));
            }
        }
    }

    @BuildStep(onlyIf = Enabled.class)
    void registerCacheInvalidatorReflection(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassBuildItem> reflection) {
        var invalidator = DotName.createSimple(PropCacheInvalidator.class);
        // Jimmer calls getMethod on the resolver/filter type to discover inherited callbacks.
        // The callback itself is invoked through PropCacheInvalidator.
        for (var type : combinedIndex.getIndex().getAllKnownImplementors(invalidator)) {
            reflection.produce(ReflectiveClassBuildItem.builder(type.name().toString()).constructors(false)
                    .reason("Jimmer cache invalidator override detection").build());
        }
    }

    @BuildStep
    JimmerCacheRetryBuildItem registerCacheFlushing(JimmerBuildTimeConfig config, Capabilities capabilities,
            List<JdbcDataSourceBuildItem> dataSources,
            BuildProducer<AdditionalBeanBuildItem> beans, BuildProducer<ExcludedTypeBuildItem> excluded) {
        boolean transactionCache = config.enable() && dataSources.stream()
                .anyMatch(source -> config.dataSources().get(source.getName()).triggerType() != TriggerType.BINLOG_ONLY);
        if (transactionCache) {
            beans.produce(AdditionalBeanBuildItem.unremovableOf(TransactionCacheOperatorFlusher.class));
        } else {
            // The scoped bean would otherwise be discovered even when no datasource uses transaction caches.
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
            JimmerConfigRecorder configRecorder,
            JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
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
                        .checkActive(configRecorder.checkActiveSupplier(dataSourceName))
                        .addInjectionPoint(
                                ParameterizedType.create(InjectableInstance.class,
                                        ClassType.create(JSqlClient.class)),
                                DataSourceUtil.isDefault(dataSourceName)
                                        ? AnnotationInstance.builder(Default.class).build()
                                        : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build())
                        .createWith(recorder.transactionJCacheOperatorFunction(dataSourceName));

                if (DataSourceUtil.isDefault(dataSourceName)) {
                    transactionCacheOperatorConfigurator.addQualifier(Default.class);
                }
                transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();
                transactionCacheOperatorConfigurator.priority(Integer.MIN_VALUE);

                syntheticBeanBuildItemBuildProducer.produce(transactionCacheOperatorConfigurator.done());
            }
        }
    }

    @BuildStep(onlyIf = KotlinEnabled.class)
    @Record(ExecutionTime.RUNTIME_INIT)
    void setTransactionKCacheOperatorBean(JimmerTransactionCacheOperatorRecorder recorder,
            JimmerConfigRecorder configRecorder,
            JimmerBuildTimeConfig buildTimeConfig,
            List<JdbcDataSourceBuildItem> jdbcDataSourceBuildItems,
            BuildProducer<SyntheticBeanBuildItem> syntheticBeanBuildItemBuildProducer) {
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
                        .checkActive(configRecorder.checkActiveSupplier(dataSourceName))
                        .addInjectionPoint(
                                ParameterizedType.create(InjectableInstance.class,
                                        ClassType.create(KSqlClient.class)),
                                DataSourceUtil.isDefault(dataSourceName)
                                        ? AnnotationInstance.builder(Default.class).build()
                                        : AnnotationInstance.builder(DataSource.class).add("value", dataSourceName).build())
                        .createWith(recorder.transactionKCacheOperatorFunction(dataSourceName));

                if (DataSourceUtil.isDefault(dataSourceName)) {
                    transactionCacheOperatorConfigurator.addQualifier(Default.class);
                }
                transactionCacheOperatorConfigurator.addQualifier().annotation(DataSource.class)
                        .addValue("value", dataSourceName).done();
                transactionCacheOperatorConfigurator.priority(Integer.MIN_VALUE);

                syntheticBeanBuildItemBuildProducer.produce(transactionCacheOperatorConfigurator.done());
            }
        }
    }
}
