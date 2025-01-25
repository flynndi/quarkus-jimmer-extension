package io.quarkiverse.jimmer.runtime.repo.support;

import java.util.Collections;
import java.util.Map;

public class RepoOperationsData {

    private static volatile Map<String, Class<?>> entityToClassUnit = Collections.emptyMap();

    public static void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        RepoOperationsData.entityToClassUnit = entityToClassUnit;
    }

    public static Class<?> getEntityClass(Class<?> clazz) {
        String clazzName = clazz.getName();
        return entityToClassUnit.get(clazzName);
    }
}
