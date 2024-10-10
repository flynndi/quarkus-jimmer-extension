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
import io.quarkiverse.jimmer.runtime.repository.KRepository;
import io.quarkiverse.jimmer.runtime.repository.support.JRepositoryImpl;
import io.quarkiverse.jimmer.runtime.repository.support.KRepositoryImpl;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.arc.Unremovable;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
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
            if (!dataSourceName.equals(DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
                classCreator.addAnnotation(DataSource.class).add("value", dataSourceName);
            }

            FieldCreator delegateCreator = classCreator
                    .getFieldCreator("delegate", JRepository.class.getName())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            try (MethodCreator methodCreator = classCreator.getMethodCreator("<init>", "V")) {
                methodCreator.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class),
                        methodCreator.getThis());
                ResultHandle entityType = methodCreator.loadClassFromTCCL(entityTypeStr);
                ResultHandle containerHandle = methodCreator
                        .invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));

                ResultHandle instanceHandle;
                if (!dataSourceName.equals(DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
                    ResultHandle annotationArray = methodCreator.newArray(Annotation[].class, 1);
                    ResultHandle arrayValue = methodCreator.newInstance(
                            MethodDescriptor.ofConstructor(DataSource.DataSourceLiteral.class, String.class),
                            methodCreator.load(dataSourceName));
                    methodCreator.writeArrayValue(annotationArray, 0, arrayValue);
                    instanceHandle = methodCreator.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, methodCreator.loadClass(JSqlClient.class), annotationArray);
                } else {
                    instanceHandle = methodCreator.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, methodCreator.loadClass(JSqlClient.class), methodCreator.loadNull());
                }
                ResultHandle beanInstanceHandle = methodCreator
                        .invokeInterfaceMethod(MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class),
                                instanceHandle);

                ResultHandle resultHandle = methodCreator.newInstance(
                        MethodDescriptor.ofConstructor(JRepositoryImpl.class, JSqlClient.class, Class.class),
                        beanInstanceHandle, entityType);
                methodCreator.writeInstanceField(delegateCreator.getFieldDescriptor(), methodCreator.getThis(), resultHandle);
                methodCreator.returnValue(null);
            }

            for (MethodInfo methodInfo : methodInfos) {
                try (MethodCreator methodCreator = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo))) {
                    ResultHandle delegate = methodCreator.readInstanceField(delegateCreator.getFieldDescriptor(),
                            methodCreator.getThis());
                    ResultHandle[] args = new ResultHandle[methodInfo.parametersCount()];
                    for (int i = 0; i < methodInfo.parametersCount(); i++) {
                        args[i] = methodCreator.getMethodParam(i);
                    }
                    methodCreator.returnValue(methodCreator.invokeInterfaceMethod(methodInfo, delegate, args));
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
            if (!dataSourceName.equals(DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
                classCreator.addAnnotation(DataSource.class).add("value", dataSourceName);
            }

            FieldCreator delegateCreator = classCreator
                    .getFieldCreator("delegate", KRepository.class.getName())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            try (MethodCreator methodCreator = classCreator.getMethodCreator("<init>", "V")) {
                methodCreator.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class),
                        methodCreator.getThis());
                ResultHandle entityType = methodCreator.loadClassFromTCCL(entityTypeStr);
                ResultHandle containerHandle = methodCreator
                        .invokeStaticMethod(MethodDescriptor.ofMethod(Arc.class, "container", ArcContainer.class));

                ResultHandle instanceHandle;
                if (!dataSourceName.equals(DataSourceUtil.DEFAULT_DATASOURCE_NAME)) {
                    ResultHandle annotationArray = methodCreator.newArray(Annotation[].class, 1);
                    ResultHandle arrayValue = methodCreator.newInstance(
                            MethodDescriptor.ofConstructor(DataSource.DataSourceLiteral.class, String.class),
                            methodCreator.load(dataSourceName));
                    methodCreator.writeArrayValue(annotationArray, 0, arrayValue);
                    instanceHandle = methodCreator.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, methodCreator.loadClass(KSqlClient.class), annotationArray);
                } else {
                    instanceHandle = methodCreator.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(ArcContainer.class, "instance", InstanceHandle.class, Class.class,
                                    Annotation[].class),
                            containerHandle, methodCreator.loadClass(KSqlClient.class), methodCreator.loadNull());
                }
                ResultHandle beanInstanceHandle = methodCreator
                        .invokeInterfaceMethod(MethodDescriptor.ofMethod(InstanceHandle.class, "get", Object.class),
                                instanceHandle);

                ResultHandle resultHandle = methodCreator.newInstance(
                        MethodDescriptor.ofConstructor(KRepositoryImpl.class, KSqlClient.class, Class.class),
                        beanInstanceHandle, entityType);
                methodCreator.writeInstanceField(delegateCreator.getFieldDescriptor(), methodCreator.getThis(), resultHandle);
                methodCreator.returnValue(null);
            }

            for (MethodInfo methodInfo : methodInfos) {
                try (MethodCreator methodCreator = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo))) {
                    ResultHandle delegate = methodCreator.readInstanceField(delegateCreator.getFieldDescriptor(),
                            methodCreator.getThis());
                    ResultHandle[] args = new ResultHandle[methodInfo.parametersCount()];
                    for (int i = 0; i < methodInfo.parametersCount(); i++) {
                        args[i] = methodCreator.getMethodParam(i);
                    }
                    methodCreator.returnValue(methodCreator.invokeInterfaceMethod(methodInfo, delegate, args));
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
