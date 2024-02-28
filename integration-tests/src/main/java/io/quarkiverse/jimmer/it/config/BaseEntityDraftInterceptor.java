package io.quarkiverse.jimmer.it.config;

import java.time.LocalDateTime;

import jakarta.inject.Singleton;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.sql.DraftInterceptor;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.it.entity.BaseEntity;
import io.quarkiverse.jimmer.it.entity.BaseEntityDraft;
import io.quarkiverse.jimmer.it.entity.BaseEntityProps;
import io.quarkus.arc.Unremovable;

@Singleton
@Unremovable
public class BaseEntityDraftInterceptor implements DraftInterceptor<BaseEntity, BaseEntityDraft> {

    @Override
    public void beforeSave(BaseEntityDraft draft, @Nullable BaseEntity original) {
        if (!ImmutableObjects.isLoaded(draft, BaseEntityProps.MODIFIED_TIME)) {
            draft.setModifiedTime(LocalDateTime.now());
        }
    }
}
