package io.quarkiverse.jimmer.runtime.repository.support;

import java.util.Collections;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableType;

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
}
