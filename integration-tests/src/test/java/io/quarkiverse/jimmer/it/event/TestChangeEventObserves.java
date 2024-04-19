package io.quarkiverse.jimmer.it.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookProps;
import io.quarkiverse.jimmer.it.entity.BookStoreProps;

@ApplicationScoped
public class TestChangeEventObserves {

    private final List<EntityEvent<?>> entityEventStorage = new CopyOnWriteArrayList<>();

    private final List<AssociationEvent> associationEventStorageOne = new CopyOnWriteArrayList<>();

    private final List<AssociationEvent> associationEventStorageTwo = new CopyOnWriteArrayList<>();

    public void entityChangeEvent(@Observes EntityEvent<?> entityEvent) {
        if (entityEvent.getImmutableType().getJavaClass() == Book.class) {
            entityEventStorage.add(entityEvent);
        }
    }

    public void associationChangeEvent(@Observes AssociationEvent associationEvent) {
        if (associationEvent.isChanged(BookProps.STORE)) {
            associationEventStorageOne.add(associationEvent);
        } else if (associationEvent.isChanged(BookStoreProps.BOOKS)) {
            associationEventStorageTwo.add(associationEvent);
        }
    }

    public List<EntityEvent<?>> getEntityEventStorage() {
        return entityEventStorage;
    }

    public List<AssociationEvent> getAssociationEventStorageOne() {
        return associationEventStorageOne;
    }

    public List<AssociationEvent> getAssociationEventStorageTwo() {
        return associationEventStorageTwo;
    }
}
