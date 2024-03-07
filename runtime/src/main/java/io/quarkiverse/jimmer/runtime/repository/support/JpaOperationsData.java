package io.quarkiverse.jimmer.runtime.repository.support;

import java.util.Collections;
import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableType;

public class JpaOperationsData {

    private static volatile Map<String, String> entityToPersistenceUnit = Collections.emptyMap();

    private static volatile Map<String, ImmutableType> entityToImmutableTypeUnit = Collections.emptyMap();

    private static volatile Map<String, Class<?>> entityToClassUnit = Collections.emptyMap();

    public static void setEntityToPersistenceUnit(Map<String, String> map) {
        entityToPersistenceUnit = Collections.unmodifiableMap(map);
    }

    public static void setEntityToImmutableTypeUnit(Map<String, ImmutableType> entityToImmutableTypeUnit) {
        JpaOperationsData.entityToImmutableTypeUnit = entityToImmutableTypeUnit;
    }

    public static void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        JpaOperationsData.entityToClassUnit = entityToClassUnit;
    }
}
