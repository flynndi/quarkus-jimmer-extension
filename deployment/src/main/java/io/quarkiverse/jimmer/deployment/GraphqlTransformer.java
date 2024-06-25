package io.quarkiverse.jimmer.deployment;

import java.util.Collection;

import org.babyfish.jimmer.sql.Entity;
import org.eclipse.microprofile.graphql.Name;
import org.jboss.jandex.*;

import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

final class GraphqlTransformer implements AnnotationsTransformer {

    private static final DotName ENTITY = DotName.createSimple(Entity.class);

    private static final DotName NAME = DotName.createSimple(Name.class);

    private final CombinedIndexBuildItem combinedIndex;

    public GraphqlTransformer(CombinedIndexBuildItem combinedIndex) {
        this.combinedIndex = combinedIndex;
    }

    @Override
    public boolean appliesTo(AnnotationTarget.Kind kind) {
        return kind == AnnotationTarget.Kind.CLASS;
    }

    @Override
    public void transform(TransformationContext transformationContext) {
        Collection<AnnotationInstance> entityAnnotationInstances = combinedIndex.getComputingIndex().getAnnotations(ENTITY);
        for (AnnotationInstance entityInstance : entityAnnotationInstances) {
            Collection<ClassInfo> allKnownImplementors = combinedIndex.getComputingIndex()
                    .getAllKnownImplementors(entityInstance.target().asClass().name());
            for (ClassInfo classInfo : allKnownImplementors) {
                transformationContext
                        .transform()
                        .add(NAME,
                                AnnotationValue.createStringValue("value", classInfo.name().withoutPackagePrefix()))
                        .done();
            }
        }
    }
}
