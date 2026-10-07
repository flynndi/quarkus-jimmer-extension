package io.quarkiverse.jimmer.runtime.event;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.util.TypeLiteral;

import org.babyfish.jimmer.sql.event.AssociationEvent;
import org.babyfish.jimmer.sql.event.EntityEvent;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.babyfish.jimmer.sql.event.Triggers;
import org.babyfish.jimmer.sql.runtime.Initializer;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import io.quarkus.agroal.DataSource;

/** Bridges Jimmer triggers to CDI; the generated subclass supplies concrete entity event injection points. */
public class QuarkusEventDispatcher {

    private final Event<Object> events;

    public QuarkusEventDispatcher(Event<Object> events) {
        this.events = events;
    }

    protected void registerEntityEvents(Map<Class<?>, Event<?>> publishers) {
        // Overridden by the generated bean. Manual clients can retain wildcard delivery when auto-configuration is off.
    }

    @SuppressWarnings("unchecked")
    public Initializer initializer(String dataSourceName) {
        Annotation[] qualifiers = { Default.Literal.INSTANCE, new DataSource.DataSourceLiteral(dataSourceName) };
        Map<Class<?>, Event<?>> publishers = new HashMap<>();
        registerEntityEvents(publishers);
        publishers.replaceAll((type, publisher) -> publisher.select(qualifiers));
        Event<EntityEvent<?>> fallback = events.select(new TypeLiteral<EntityEvent<?>>() {
        }, qualifiers);
        Event<AssociationEvent> associations = events.select(AssociationEvent.class, qualifiers);
        Consumer<EntityEvent<?>> publishEntity = event -> {
            // Eviction has no old/new entity. Jimmer's metadata identifies the entity interface in every case.
            Event<EntityEvent<?>> publisher = (Event<EntityEvent<?>>) publishers
                    .getOrDefault(event.getImmutableType().getJavaClass(), fallback);
            // The injection point retains the concrete type; CDI also notifies matching wildcard observers once.
            publisher.fire(event);
        };
        return sqlClient -> {
            Triggers[] triggersArr = ((JSqlClientImplementor) sqlClient).getTriggerType() == TriggerType.BOTH
                    ? new Triggers[] { sqlClient.getTriggers(), sqlClient.getTriggers(true) }
                    : new Triggers[] { sqlClient.getTriggers() };
            for (Triggers triggers : triggersArr) {
                triggers.addEntityListener(publishEntity::accept);
                triggers.addAssociationListener(associations::fire);
            }
        };
    }
}
