package io.quarkiverse.jimmer.deployment.cdi;

import java.lang.reflect.Modifier;
import java.util.Map;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.jboss.jandex.DotName;

import io.quarkiverse.jimmer.deployment.cfg.JimmerBuildConditions.Enabled;
import io.quarkiverse.jimmer.runtime.cdi.QuarkusEventDispatcher;
import io.quarkiverse.jimmer.runtime.util.Constant;
import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.BuildSteps;
import io.quarkus.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.SignatureBuilder;
import io.quarkus.gizmo.Type;

@BuildSteps(onlyIf = Enabled.class)
final class JimmerEventProcessor {

    @BuildStep
    AdditionalApplicationArchiveMarkerBuildItem indexEntityLibraries() {
        // Include entity libraries without a Jandex index, using the same marker as Jimmer's EntityManager.
        return new AdditionalApplicationArchiveMarkerBuildItem(Constant.ENTITIES_RESOURCE);
    }

    @BuildStep
    void generateEventDispatcher(CombinedIndexBuildItem index, BuildProducer<GeneratedBeanBuildItem> generatedBeans) {
        var entityNames = index.getIndex().getAnnotations(DotName.createSimple(Entity.class)).stream()
                .map(annotation -> annotation.target().asClass().name().toString()).sorted().toList();
        try (ClassCreator creator = ClassCreator.builder().classOutput(new GeneratedBeanGizmoAdaptor(generatedBeans))
                .className(QuarkusEventDispatcher.class.getPackageName() + ".GeneratedQuarkusEventDispatcher")
                .superClass(QuarkusEventDispatcher.class).build()) {
            creator.addAnnotation(Singleton.class);
            creator.addAnnotation(Unremovable.class);
            // The runtime superclass and generated application bean can use different class loaders in dev mode.
            // Constructor injection avoids package-private access to inherited injection points across those loaders.
            var constructor = creator.getMethodCreator("<init>", void.class, Event.class);
            constructor.addAnnotation(Inject.class);
            constructor.getParameterAnnotations(0).addAnnotation(Any.class);
            constructor.setSignature(SignatureBuilder.forMethod().setReturnType(Type.voidType())
                    .addParameterType(Type.parameterizedType(Type.classType(Event.class), Type.classType(Object.class)))
                    .build());
            constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(QuarkusEventDispatcher.class, Event.class),
                    constructor.getThis(), constructor.getMethodParam(0));
            constructor.returnVoid();
            var register = creator.getMethodCreator("registerEntityEvents", void.class, Map.class)
                    .setModifiers(Modifier.PROTECTED);
            int fieldIndex = 0;
            for (String entityName : entityNames) {
                var field = creator.getFieldCreator("entityEvents" + fieldIndex++, Event.class).setModifiers(0);
                field.addAnnotation(Inject.class);
                field.addAnnotation(Any.class);
                // ArC resolves Event<EntityEvent<Book>> from this signature.
                field.setSignature(SignatureBuilder.forField().setType(Type.parameterizedType(Type.classType(Event.class),
                        Type.parameterizedType(Type.classType(EntityEvent.class), Type.classType(entityName)))).build());
                register.invokeInterfaceMethod(MethodDescriptor.ofMethod(Map.class, "put", Object.class,
                        Object.class, Object.class), register.getMethodParam(0), register.loadClassFromTCCL(entityName),
                        register.readInstanceField(field.getFieldDescriptor(), register.getThis()));
            }
            register.returnVoid();
        }
    }
}
