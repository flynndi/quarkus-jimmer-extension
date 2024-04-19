package io.quarkiverse.jimmer.it.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.it.entity.Book;
import io.quarkiverse.jimmer.it.entity.BookProps;
import io.quarkiverse.jimmer.it.entity.BookStoreProps;

@ApplicationScoped
public class ChangeEventObserves {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangeEventObserves.class);

    public void entityChangeEvent(@Observes EntityEvent<?> entityEvent) {
        if (entityEvent.getImmutableType().getJavaClass() == Book.class) {
            LOGGER.info("The object `Book` is changed \told: {}, \tnew: {}", entityEvent.getOldEntity(),
                    entityEvent.getNewEntity());
        }
    }

    public void associationChangeEvent(@Observes AssociationEvent associationEvent) {
        if (associationEvent.isChanged(BookProps.STORE)) {
            LOGGER.info(
                    "The many-to-one association `Book.store` is changed, \tbook id: {}, \tdetached book store id: {}, \tattached book store id: {}",
                    associationEvent.getSourceId(), associationEvent.getDetachedTargetId(),
                    associationEvent.getAttachedTargetId());
        } else if (associationEvent.isChanged(BookStoreProps.BOOKS)) {
            LOGGER.info(
                    "The one-to-many association `BookStore.books` is changed, \tbook store id: {}, \tdetached book id: {}, \tattached book id: {}",
                    associationEvent.getSourceId(), associationEvent.getDetachedTargetId(),
                    associationEvent.getAttachedTargetId());
        }
    }
}
