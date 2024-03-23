package io.quarkiverse.jimmer.runtime.repository;

import java.util.Map;

import io.quarkiverse.jimmer.runtime.repository.support.JpaOperationsData;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class JimmerJpaRecorder {

    public void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        JpaOperationsData.setEntityToClassUnit(entityToClassUnit);
    }
}
