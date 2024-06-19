package io.quarkiverse.jimmer.deployment;

import java.beans.Introspector;

import org.babyfish.jimmer.sql.TransientResolver;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.CustomScopeAnnotationsBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.arc.processor.DotNames;

final class TransientResolverTransformer implements AnnotationsTransformer {

    private static final DotName TRANSIENT_RESOLVER = DotName.createSimple(TransientResolver.class.getName());

    private final CustomScopeAnnotationsBuildItem customScopes;

    public TransientResolverTransformer(CustomScopeAnnotationsBuildItem customScopes) {
        this.customScopes = customScopes;
    }

    @Override
    public boolean appliesTo(AnnotationTarget.Kind kind) {
        return kind == AnnotationTarget.Kind.CLASS;
    }

    @Override
    public void transform(TransformationContext transformationContext) {
        AnnotationTarget target = transformationContext.getTarget();
        if (target.kind() != AnnotationTarget.Kind.CLASS) {
            return;
        }
        ClassInfo classInfo = target.asClass();
        if (classInfo.interfaceNames().contains(TRANSIENT_RESOLVER) && customScopes.isScopeDeclaredOn(classInfo)) {
            transformationContext
                    .transform()
                    .add(DotNames.NAMED,
                            AnnotationValue.createStringValue("value", Introspector.decapitalize(classInfo.simpleName())))
                    .done();
        }
    }
}
