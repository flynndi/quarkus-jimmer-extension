package io.quarkiverse.jimmer.runtime.repo.support;

import java.util.Map;

import io.quarkus.runtime.annotations.Recorder;
import kotlin.reflect.KClass;

@Recorder
public class JimmerJpaRecorder {

    public void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        JpaOperationsData.setEntityToClassUnit(entityToClassUnit);
    }

    public void setEntityToKClassUnit(Map<String, KClass<?>> entityToKClassUnit) {
        JpaOperationsData.setEntityToKClassUnit(entityToKClassUnit);
    }
}
