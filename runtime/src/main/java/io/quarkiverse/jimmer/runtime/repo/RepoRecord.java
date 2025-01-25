package io.quarkiverse.jimmer.runtime.repo;

import java.util.Map;

import io.quarkiverse.jimmer.runtime.repo.support.RepoOperationsData;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RepoRecord {

    public void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        RepoOperationsData.setEntityToClassUnit(entityToClassUnit);
    }
}
