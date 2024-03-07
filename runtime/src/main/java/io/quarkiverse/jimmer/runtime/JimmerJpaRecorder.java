package io.quarkiverse.jimmer.runtime;

import java.util.Map;

import org.babyfish.jimmer.meta.ImmutableType;

import io.quarkiverse.jimmer.runtime.repository.support.JpaOperationsData;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerJpaRecorder {

    public void setEntityToPersistenceUnit(Map<String, String> entityToPersistenceUnit) {
        JpaOperationsData.setEntityToPersistenceUnit(entityToPersistenceUnit);
    }

    public void setEntityToImmutableTypeUnit(Map<String, ImmutableType> entityToImmutableTypeUnit) {
        JpaOperationsData.setEntityToImmutableTypeUnit(entityToImmutableTypeUnit);
    }

    public void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        JpaOperationsData.setEntityToClassUnit(entityToClassUnit);
    }
}
