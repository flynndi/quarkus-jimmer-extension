package io.quarkiverse.jimmer.deployment.jackson;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassConditionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveHierarchyIgnoreWarningBuildItem;

@BuildSteps(onlyIf = Enabled.class)
final class JimmerJackson3Processor {

    @BuildStep
    void guardJackson3Reflection(CombinedIndexBuildItem combinedIndex,
            BuildProducer<ReflectiveClassConditionBuildItem> conditions) {
        // Hierarchy scans of Jimmer's codec interfaces can register their optional Jackson 3 implementations.
        for (var type : combinedIndex.getIndex().getClassesInPackage("org.babyfish.jimmer.jackson.v3")) {
            conditions.produce(new ReflectiveClassConditionBuildItem(type.name().toString(),
                    "tools.jackson.databind.ObjectMapper"));
        }
    }

    @BuildStep
    ReflectiveHierarchyIgnoreWarningBuildItem ignoreJackson3ReflectionWarnings() {
        // The Jackson 2 annotation scan can encounter optional Jackson 3 signatures in jimmer-core.
        return new ReflectiveHierarchyIgnoreWarningBuildItem(dotName -> dotName.toString().startsWith("tools.jackson."));
    }
}
