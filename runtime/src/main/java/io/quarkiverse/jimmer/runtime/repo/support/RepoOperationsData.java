package io.quarkiverse.jimmer.runtime.repo.support;

import java.util.Collections;
import java.util.Map;

/**
 * Internal entity-type registry populated by the extension during initialization.
 * Its public visibility supports the recorder and repository implementations;
 * it is not an application registration API.
 */
public class RepoOperationsData {

    private static volatile Map<String, Class<?>> entityToClassUnit = Collections.emptyMap();

    public static void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        RepoOperationsData.entityToClassUnit = entityToClassUnit;
    }

    public static Class<?> getEntityClass(Class<?> clazz) {
        Map<String, Class<?>> registeredTypes = entityToClassUnit;
        // ArC creates subclasses for intercepted/decorated beans. Their repository superclass is indexed.
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            Class<?> entityClass = registeredTypes.get(current.getName());
            if (entityClass != null) {
                return entityClass;
            }
        }
        throw new IllegalArgumentException("No Jimmer entity type was registered for repository " + clazz.getName());
    }
}
