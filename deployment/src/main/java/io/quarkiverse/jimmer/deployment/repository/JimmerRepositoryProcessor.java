package io.quarkiverse.jimmer.deployment.repository;

import java.util.Collection;
import java.util.List;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.Type;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.JavaEnabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.KotlinEnabled;
import io.quarkiverse.jimmer.deployment.repository.bytecode.JimmerRepositoryFactory;
import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.KRepository;
import io.quarkiverse.jimmer.runtime.repository.support.JRepositoryImpl;
import io.quarkiverse.jimmer.runtime.repository.support.KRepositoryImpl;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.ClassOutput;

@BuildSteps(onlyIf = Enabled.class)
final class JimmerRepositoryProcessor {

    @BuildStep(onlyIf = JavaEnabled.class)
    void contributeJRepositoryToIndex(BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClasses) {
        additionalIndexedClasses
                .produce(new AdditionalIndexedClassesBuildItem(JRepository.class.getName(), JRepositoryImpl.class.getName()));
    }

    @BuildStep(onlyIf = KotlinEnabled.class)
    void contributeKRepositoryToIndex(BuildProducer<AdditionalIndexedClassesBuildItem> additionalIndexedClasses) {
        additionalIndexedClasses
                .produce(new AdditionalIndexedClassesBuildItem(KRepository.class.getName(), KRepositoryImpl.class.getName()));
    }

    // Only legacy JRepository/KRepository interfaces participate in derived-query generation.
    @BuildStep
    void collectRepositoryMetadata(CombinedIndexBuildItem combinedIndex,
            BuildProducer<RepositoryMetadata> repositoryMetadataBuildProducer) {
        Collection<ClassInfo> jRepositoryInterfaces = combinedIndex.getIndex().getAllKnownSubinterfaces(JRepository.class);
        for (ClassInfo repositoryInterface : jRepositoryInterfaces) {
            AnnotationInstance dataSource = repositoryInterface.declaredAnnotation(DotName.createSimple(DataSource.class));
            String dataSourceName = dataSource != null ? dataSource.value().asString() : DataSourceUtil.DEFAULT_DATASOURCE_NAME;
            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryInterface.name(),
                    DotName.createSimple(JRepository.class), combinedIndex.getIndex());
            repositoryMetadataBuildProducer
                    .produce(new RepositoryMetadata(JandexReflection.loadRawType(typeParameters.get(0)),
                            JandexReflection.loadClass(repositoryInterface), dataSourceName));
        }
        Collection<ClassInfo> kRepositoryInterfaces = combinedIndex.getIndex().getAllKnownSubinterfaces(KRepository.class);
        for (ClassInfo repositoryInterface : kRepositoryInterfaces) {
            AnnotationInstance dataSource = repositoryInterface.declaredAnnotation(DotName.createSimple(DataSource.class));
            String dataSourceName = dataSource != null ? dataSource.value().asString() : DataSourceUtil.DEFAULT_DATASOURCE_NAME;
            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryInterface.name(),
                    DotName.createSimple(KRepository.class), combinedIndex.getIndex());
            repositoryMetadataBuildProducer
                    .produce(new RepositoryMetadata(JandexReflection.loadRawType(typeParameters.get(0)),
                            JandexReflection.loadClass(repositoryInterface), dataSourceName));
        }
    }

    @BuildStep
    void generateRepositoryImpl(List<RepositoryMetadata> repositoryBuildItems,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanBuildItem) {
        if (repositoryBuildItems.isEmpty()) {
            return;
        }
        ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeanBuildItem);
        for (RepositoryMetadata metadata : repositoryBuildItems) {
            JimmerRepositoryFactory jimmerRepositoryFactory = new JimmerRepositoryFactory(metadata);
            classOutput.write(jimmerRepositoryFactory.getTargetRepositoryClassName(),
                    jimmerRepositoryFactory.getTargetRepositoryBytes());
        }
    }
}
