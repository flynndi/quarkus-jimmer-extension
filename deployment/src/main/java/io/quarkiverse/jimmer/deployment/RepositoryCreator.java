package io.quarkiverse.jimmer.deployment;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.kt.KSqlClient;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;

import io.quarkiverse.jimmer.runtime.repository.JRepository;
import io.quarkiverse.jimmer.runtime.repository.JRepositoryImpl;
import io.quarkiverse.jimmer.runtime.repository.KRepository;
import io.quarkiverse.jimmer.runtime.repository.KRepositoryImpl;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import io.quarkus.gizmo.*;
import io.quarkus.runtime.util.HashUtil;

final class RepositoryCreator {

    private final ClassOutput classOutput;

    private final List<MethodInfo> methodInfos;

    private final DotName repositoryName;

    private final String dataSourceName;

    private final Map.Entry<DotName, DotName> dotIdDotNameEntry;

    public RepositoryCreator(ClassOutput classOutput, List<MethodInfo> methodInfos, DotName repositoryName,
            String dataSourceName, Map.Entry<DotName, DotName> dotIdDotNameEntry) {
        this.classOutput = classOutput;
        this.methodInfos = methodInfos;
        this.repositoryName = repositoryName;
        this.dataSourceName = dataSourceName;
        this.dotIdDotNameEntry = dotIdDotNameEntry;
    }

    Result implementCrudJRepository() {
        DotName idTypeDotName = dotIdDotNameEntry.getKey();
        DotName entityDotName = dotIdDotNameEntry.getValue();
        String entityTypeStr = entityDotName.toString();
        String repositoryNameStr = repositoryName.toString();
        String generatedClassName = repositoryNameStr + "_" + HashUtil.sha1(repositoryNameStr) + "Impl";

        try (ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                .className(generatedClassName)
                .interfaces(repositoryNameStr)
                .build()) {
            classCreator.addAnnotation(ApplicationScoped.class);
            classCreator.addAnnotation(Unremovable.class);
            if (!dataSourceName.equals("<default>")) {
                classCreator.addAnnotation(DataSource.class).add("value", dataSourceName);
            }

            FieldCreator delegateCreator = classCreator
                    .getFieldCreator("delegate", JRepository.class.getName())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            try (MethodCreator ctor = classCreator.getMethodCreator("<init>", "V")) {
                ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class), ctor.getThis());
                ResultHandle entityType = ctor.loadClassFromTCCL(entityTypeStr);
                ResultHandle containerHandle = ctor
                        .invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));

                ResultHandle instanceHandle;
                if (!dataSourceName.equals("<default>")) {
                    ResultHandle annotationArray = ctor.newArray(Annotation[].class, 1);
                    ResultHandle arrayValue = ctor.newInstance(
                            MethodDescriptor.ofConstructor(DataSource.DataSourceLiteral.class, String.class),
                            ctor.load(dataSourceName));
                    ctor.writeArrayValue(annotationArray, 0, arrayValue);
                    instanceHandle = ctor.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, ctor.loadClass(JSqlClient.class), annotationArray);
                } else {
                    instanceHandle = ctor.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, ctor.loadClass(JSqlClient.class), ctor.loadNull());
                }
                ResultHandle beanInstanceHandle = ctor
                        .invokeInterfaceMethod(MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class),
                                instanceHandle);

                ResultHandle resultHandle = ctor.newInstance(
                        MethodDescriptor.ofConstructor(JRepositoryImpl.class, JSqlClient.class, Class.class),
                        beanInstanceHandle, entityType);
                ctor.writeInstanceField(delegateCreator.getFieldDescriptor(), ctor.getThis(), resultHandle);
                ctor.returnValue(null);
            }

            for (MethodInfo methodInfo : methodInfos) {
                try (MethodCreator ctor = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo))) {
                    ResultHandle delegate = ctor.readInstanceField(delegateCreator.getFieldDescriptor(),
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

    Result implementCrudKRepository() {
        DotName idTypeDotName = dotIdDotNameEntry.getKey();
        DotName entityDotName = dotIdDotNameEntry.getValue();
        String entityTypeStr = entityDotName.toString();
        String repositoryNameStr = repositoryName.toString();
        String generatedClassName = repositoryNameStr + "_" + HashUtil.sha1(repositoryNameStr) + "Impl";

        try (ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                .className(generatedClassName)
                .interfaces(repositoryNameStr)
                .build()) {
            classCreator.addAnnotation(ApplicationScoped.class);
            classCreator.addAnnotation(Unremovable.class);
            if (!dataSourceName.equals("<default>")) {
                classCreator.addAnnotation(DataSource.class).add("value", dataSourceName);
            }

            FieldCreator delegateCreator = classCreator
                    .getFieldCreator("delegate", KRepository.class.getName())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            try (MethodCreator ctor = classCreator.getMethodCreator("<init>", "V")) {
                ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class), ctor.getThis());
                ResultHandle entityType = ctor.loadClassFromTCCL(entityTypeStr);
                ResultHandle containerHandle = ctor
                        .invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));

                ResultHandle instanceHandle;
                if (!dataSourceName.equals("<default>")) {
                    ResultHandle annotationArray = ctor.newArray(Annotation[].class, 1);
                    ResultHandle arrayValue = ctor.newInstance(
                            MethodDescriptor.ofConstructor(DataSource.DataSourceLiteral.class, String.class),
                            ctor.load(dataSourceName));
                    ctor.writeArrayValue(annotationArray, 0, arrayValue);
                    instanceHandle = ctor.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, ctor.loadClass(KSqlClient.class), annotationArray);
                } else {
                    instanceHandle = ctor.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, ctor.loadClass(KSqlClient.class), ctor.loadNull());
                }
                ResultHandle beanInstanceHandle = ctor
                        .invokeInterfaceMethod(MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class),
                                instanceHandle);

                ResultHandle resultHandle = ctor.newInstance(
                        MethodDescriptor.ofConstructor(KRepositoryImpl.class, KSqlClient.class, Class.class),
                        beanInstanceHandle, entityType);
                ctor.writeInstanceField(delegateCreator.getFieldDescriptor(), ctor.getThis(), resultHandle);
                ctor.returnValue(null);
            }

            for (MethodInfo methodInfo : methodInfos) {
                try (MethodCreator ctor = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo))) {
                    ResultHandle delegate = ctor.readInstanceField(delegateCreator.getFieldDescriptor(),
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
