package io.quarkiverse.jimmer.deployment.repo;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.JandexReflection;
import org.jboss.jandex.Type;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.JavaEnabled;
import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.KotlinEnabled;
import io.quarkiverse.jimmer.runtime.repo.RepoRecord;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractJavaRepository;
import io.quarkiverse.jimmer.runtime.repo.support.AbstractKotlinRepository;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.util.JandexUtil;

@BuildSteps(onlyIf = Enabled.class)
final class JimmerRepoProcessor {

    // Application-owned repository classes need entity metadata, not a generated implementation.
    @BuildStep(onlyIf = JavaEnabled.class)
    void analyzeJavaRepository(CombinedIndexBuildItem combinedIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            BuildProducer<EntityToClassBuildItem> entityToClassProducer) {
        Collection<ClassInfo> repositoryBeans = combinedIndex.getIndex()
                .getAllKnownSubclasses(AbstractJavaRepository.class);
        for (ClassInfo repositoryBean : repositoryBeans) {
            unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(repositoryBean.name()));

            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryBean.name(),
                    DotName.createSimple(AbstractJavaRepository.class), combinedIndex.getComputingIndex());
            entityToClassProducer.produce(new EntityToClassBuildItem(repositoryBean.name().toString(),
                    JandexReflection.loadRawType(typeParameters.get(0))));
        }
    }

    @BuildStep(onlyIf = KotlinEnabled.class)
    void analyzeKotlinRepository(CombinedIndexBuildItem combinedIndex,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeanProducer,
            BuildProducer<EntityToClassBuildItem> entityToClassProducer) {
        Collection<ClassInfo> repositoryBeans = combinedIndex.getIndex()
                .getAllKnownSubclasses(AbstractKotlinRepository.class);
        for (ClassInfo repositoryBean : repositoryBeans) {
            unremovableBeanProducer.produce(UnremovableBeanBuildItem.beanTypes(repositoryBean.name()));

            List<Type> typeParameters = JandexUtil.resolveTypeParameters(repositoryBean.name(),
                    DotName.createSimple(AbstractKotlinRepository.class), combinedIndex.getComputingIndex());
            entityToClassProducer.produce(new EntityToClassBuildItem(repositoryBean.name().toString(),
                    JandexReflection.loadRawType(typeParameters.get(0))));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void recordRepoOperationsData(RepoRecord repoRecord,
            List<EntityToClassBuildItem> entityToClassBuildItems) {
        Map<String, Class<?>> map = new HashMap<>();
        for (EntityToClassBuildItem entityToClassBuildItem : entityToClassBuildItems) {
            map.put(entityToClassBuildItem.getEntityClass(), entityToClassBuildItem.getClazz());
        }
        repoRecord.setEntityToClassUnit(map);
    }
}
