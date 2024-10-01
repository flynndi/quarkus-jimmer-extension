package io.quarkiverse.jimmer.deployment;

import java.lang.reflect.Modifier;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.jandex.*;

import io.quarkiverse.jimmer.runtime.repository.TestInterface;
import io.quarkiverse.jimmer.runtime.repository.TestInterfaceImpl;
import io.quarkus.arc.Unremovable;
import io.quarkus.gizmo.*;
import io.quarkus.runtime.util.HashUtil;

public class RepositoryCreator {

    private final ClassOutput classOutput;

    public RepositoryCreator(ClassOutput classOutput) {
        this.classOutput = classOutput;
    }

    public Result implementCrudRepository(ClassInfo repositoryToImplement, Map.Entry<DotName, DotName> extraTypesResult,
            Set<MethodInfo> methodInfos) {
        DotName idTypeDotName = extraTypesResult.getKey();
        String idTypeStr = idTypeDotName.toString();
        DotName entityDotName = extraTypesResult.getValue();
        String entityTypeStr = entityDotName.toString();

        String repositoryToImplementStr = repositoryToImplement.name().toString();
        String generatedClassName = repositoryToImplementStr + "_" + HashUtil.sha1(repositoryToImplementStr) + "Impl";

        try (ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                .className(generatedClassName)
                .interfaces(repositoryToImplementStr)
                .build()) {
            classCreator.addAnnotation(ApplicationScoped.class);
            classCreator.addAnnotation(Unremovable.class);

            //            FieldCreator entityClassFieldCreator = classCreator.getFieldCreator("entityType", Class.class.getName())
            //                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            //            AnnotationCreator jSqlClientAnnotationCreator = classCreator
            //                    .getFieldCreator("jSqlClient", JSqlClient.class)
            //                    .setModifiers(Modifier.PUBLIC)
            //                    .addAnnotation(Inject.class);

            FieldCreator entityClassFieldCreator = classCreator
                    .getFieldCreator("defaultImpl", TestInterface.class.getName())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            FieldCreator jSqlClientFieldCreator = classCreator.getFieldCreator("jSqlClient", JSqlClient.class.getName());
            jSqlClientFieldCreator.setModifiers(jSqlClientFieldCreator.getModifiers() & ~Modifier.PRIVATE)
                    .addAnnotation(Inject.class);

            //            MethodCreator constructor = classCreator.getMethodCreator("<init>", "V");
            //            constructor.invokeSpecialMethod(MethodDescriptor.ofConstructor(Object.class), constructor.getThis());
            //            constructor.returnValue(null);

            //            try (MethodCreator ctor = classCreator.getMethodCreator("<init>", "V")) {
            //                ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class), ctor.getThis());
            //                ctor.returnValue(null);
            //            }

            try (MethodCreator ctor = classCreator.getMethodCreator("<init>", "V")) {
                ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class), ctor.getThis());
                FieldDescriptor jSqlClientFieldDescriptor = classCreator.getFieldCreator("jSqlClient", JSqlClient.class)
                        .getFieldDescriptor();
                ResultHandle entityType = ctor.loadClassFromTCCL(entityTypeStr);
                ResultHandle resultHandle = ctor.newInstance(
                        MethodDescriptor.ofConstructor(TestInterfaceImpl.class, JSqlClient.class, Class.class),
                        ctor.readInstanceField(jSqlClientFieldDescriptor, ctor.getThis()), entityType);
                ctor.writeInstanceField(entityClassFieldCreator.getFieldDescriptor(), ctor.getThis(), resultHandle);
                ctor.returnValue(null);
            }

            for (MethodInfo methodInfo : methodInfos) {
                try (MethodCreator ctor = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo))) {
                    ResultHandle delegate = ctor.readInstanceField(entityClassFieldCreator.getFieldDescriptor(),
                            ctor.getThis());
                    ResultHandle[] args = new ResultHandle[methodInfo.parametersCount()];
                    for (int i = 0; i < methodInfo.parametersCount(); i++) {
                        args[i] = ctor.getMethodParam(i);
                    }
                    ctor.returnValue(ctor.invokeInterfaceMethod(methodInfo, delegate, args));
                }

            }
            classCreator.writeTo(classOutput);

        }

        return new Result(entityDotName, idTypeDotName, generatedClassName);
    }

    public static final class Result {
        final DotName entityDotName;
        final DotName idTypeDotName;
        final String generatedClassName;

        Result(DotName entityDotName, DotName idTypeDotName, String generatedClassName) {
            this.entityDotName = entityDotName;
            this.idTypeDotName = idTypeDotName;
            this.generatedClassName = generatedClassName;
        }

        public DotName getEntityDotName() {
            return entityDotName;
        }

        public DotName getIdTypeDotName() {
            return idTypeDotName;
        }

        public String getGeneratedClassName() {
            return generatedClassName;
        }
    }
}
