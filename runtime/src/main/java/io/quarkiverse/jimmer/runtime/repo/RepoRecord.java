package io.quarkiverse.jimmer.runtime.repo;

import java.util.Map;

import io.quarkiverse.jimmer.runtime.repo.support.RepoOperationsData;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Internal build-time/runtime bridge for repository entity metadata.
 * Applications use CDI repositories and must not populate this registry themselves.
 */
@Recorder
public class RepoRecord {

    public void setEntityToClassUnit(Map<String, Class<?>> entityToClassUnit) {
        RepoOperationsData.setEntityToClassUnit(entityToClassUnit);
    }
}
