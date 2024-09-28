package io.quarkiverse.jimmer.deployment;

import java.lang.reflect.Modifier;
import java.util.*;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.JSqlClient;
import org.jboss.jandex.*;
import org.jboss.jandex.Type;

import io.quarkus.arc.Unremovable;
import io.quarkus.deployment.util.JandexUtil;
import io.quarkus.gizmo.*;
import io.quarkus.runtime.util.HashUtil;

public class RepositoryCreator {

    private final ClassOutput classOutput;

    private final IndexView index;

    public RepositoryCreator(ClassOutput classOutput, IndexView index) {
        this.classOutput = classOutput;
        this.index = index;
    }

    public Result implementCrudRepository(ClassInfo repositoryToImplement, IndexView indexView) {
        Map.Entry<DotName, DotName> extraTypesResult = extractIdAndEntityTypes(repositoryToImplement, indexView);

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

            FieldCreator entityClassFieldCreator = classCreator.getFieldCreator("entityClass", Class.class.getName())
                    .setModifiers(Modifier.PRIVATE | Modifier.FINAL);

            AnnotationCreator jSqlClientAnnotationCreator = classCreator
                    .getFieldCreator("jSqlClient", JSqlClient.class)
                    .setModifiers(Modifier.PUBLIC)
                    .addAnnotation(ApplicationScoped.class);

            try (MethodCreator ctor = classCreator.getMethodCreator("<init>", "V")) {
                ctor.invokeSpecialMethod(MethodDescriptor.ofMethod(Object.class, "<init>", void.class), ctor.getThis());
                // initialize the entityClass field
                ctor.writeInstanceField(entityClassFieldCreator.getFieldDescriptor(), ctor.getThis(),
                        ctor.loadClassFromTCCL(entityTypeStr));
                ctor.returnValue(null);
            }

            Set<MethodInfo> methodInfos = GenerationUtil
                    .interfaceMethods(Collections.singletonList(repositoryToImplement.interfaceNames().get(0)), indexView);

            for (MethodInfo methodInfo : methodInfos) {
                try (MethodCreator ctor = classCreator.getMethodCreator(MethodDescriptor.of(methodInfo))) {
                    ctor.returnNull();
                }

            }
            classCreator.writeTo(classOutput);

        }

        return new Result(entityDotName, idTypeDotName, generatedClassName);
    }

    private Map.Entry<DotName, DotName> extractIdAndEntityTypes(ClassInfo repositoryToImplement, IndexView indexView) {

        DotName entityDotName = null;
        DotName idDotName = null;

        // we need to pull the entity and ID types for the Spring Data generic types
        // we also need to make sure that the user didn't try to specify multiple different types
        // in the same interface (which is possible if only Repository is used)
        for (DotName extendedSpringDataRepo : GenerationUtil.extendedSpringDataRepos(repositoryToImplement, indexView)) {
            List<Type> types = JandexUtil.resolveTypeParameters(repositoryToImplement.name(), extendedSpringDataRepo, index);
            if (!(types.get(0) instanceof ClassType)) {
                throw new IllegalArgumentException(
                        "Entity generic argument of " + repositoryToImplement + " is not a regular class type");
            }
            DotName newEntityDotName = types.get(0).name();
            if ((entityDotName != null) && !newEntityDotName.equals(entityDotName)) {
                throw new IllegalArgumentException("Repository " + repositoryToImplement + " specifies multiple Entity types");
            }
            entityDotName = newEntityDotName;

            DotName newIdDotName = types.get(1).name();
            if ((idDotName != null) && !newIdDotName.equals(idDotName)) {
                throw new IllegalArgumentException("Repository " + repositoryToImplement + " specifies multiple ID types");
            }
            idDotName = newIdDotName;
        }

        if (idDotName == null || entityDotName == null) {
            throw new IllegalArgumentException(
                    "Repository " + repositoryToImplement + " does not specify ID and/or Entity type");
        }

        return new AbstractMap.SimpleEntry<>(idDotName, entityDotName);
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
