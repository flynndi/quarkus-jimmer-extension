package io.quarkiverse.jimmer.runtime.repository.support;

import java.util.Collections;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableType;

import kotlin.jvm.internal.Reflection;
import kotlin.reflect.KClass;

public class JpaOperationsData {

    private static volatile Map<String, Class<?>> entityToClassUnit = Collections.emptyMap();

    public static void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        JpaOperationsData.entityToClassUnit = entityToClassUnit;
    }

    public static ImmutableType getImmutableType(Class<?> clazz) {
        String clazzName = clazz.getName();
        return ImmutableType.get(entityToClassUnit.get(clazzName));
    }

    public static Class<?> getEntityClass(Class<?> clazz) {
        String clazzName = clazz.getName();
        return entityToClassUnit.get(clazzName);
    }

    public static KClass<?> getEntityKClass(Class<?> clazz) {
        String clazzName = clazz.getName();
        Class<?> aClass = entityToClassUnit.get(clazzName);
        return Reflection.createKotlinClass(aClass);
    }
}
