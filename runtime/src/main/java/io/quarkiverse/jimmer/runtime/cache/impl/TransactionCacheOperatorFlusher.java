package io.quarkiverse.jimmer.runtime.cache.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.EventMetadata;
import jakarta.inject.Inject;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.DatabaseEvent;
import org.babyfish.jimmer.sql.event.TriggerType;
import org.babyfish.jimmer.sql.event.Triggers;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.runtime.event.QuarkusEventDispatcher;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.All;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;

@ApplicationScoped
public class TransactionCacheOperatorFlusher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionCacheOperatorFlusher.class);

    private final List<InstanceHandle<TransactionCacheOperator>> operatorHandles;

    private final Object synchronizationKey = new Object();

    private final ReentrantLock registrationLock = new ReentrantLock();

    @Inject
    TransactionSynchronizationRegistry synchronizationRegistry;

    @Inject
    TransactionManager transactionManager;

    public TransactionCacheOperatorFlusher(@All List<InstanceHandle<TransactionCacheOperator>> operatorHandles) {
        this.operatorHandles = operatorHandles;
    }

    /** Retains application-published datasource notifications as an explicit completion-flush request. */
    public void onDatabaseEvent(@Observes DatabaseEvent event, EventMetadata metadata) {
        // Event.select() retains the original injection point, including the generated dispatcher's typed events.
        // Its notifications are public events; managed clients schedule their own flushing through register().
        var injectionPoint = metadata.getInjectionPoint();
        if (injectionPoint != null && injectionPoint.getBean() != null
                && QuarkusEventDispatcher.class.isAssignableFrom(injectionPoint.getBean().getBeanClass())) {
            return;
        }
        metadata.getQualifiers().stream()
                .filter(DataSource.class::isInstance)
                .map(DataSource.class::cast)
                .map(DataSource::value)
                .findFirst()
                .ifPresent(this::scheduleFlush);
    }

    /** Attaches completion flushing only to a client created by the extension's CDI producer. */
    public void register(JSqlClient sqlClient, String dataSourceName) {
        TriggerType triggerType = ((JSqlClientImplementor) sqlClient).getTriggerType();
        if (triggerType == TriggerType.BINLOG_ONLY) {
            return;
        }
        // In BOTH mode Jimmer's caches use the binlog channel, while application mutations use the transaction channel.
        Triggers[] triggers = triggerType == TriggerType.BOTH
                ? new Triggers[] { sqlClient.getTriggers(), sqlClient.getTriggers(true) }
                : new Triggers[] { sqlClient.getTriggers() };
        for (Triggers channel : triggers) {
            channel.addEntityListener(event -> scheduleFlush(dataSourceName));
            channel.addAssociationListener(event -> scheduleFlush(dataSourceName));
        }
    }

    private void scheduleFlush(String dataSourceName) {
        // Without a JTA transaction, later Jimmer listeners may not have written the invalidation record yet.
        // Such records need a later retry; a rollback-only transaction cannot schedule a successful commit.
        if (synchronizationRegistry.getTransactionStatus() != Status.STATUS_ACTIVE) {
            return;
        }
        registrationLock.lock();
        try {
            PendingFlush pending = (PendingFlush) synchronizationRegistry.getResource(synchronizationKey);
            if (pending != null) {
                pending.dataSourceNames.add(dataSourceName);
                return;
            }
            try {
                Transaction transaction = transactionManager.getTransaction();
                if (transaction == null) {
                    return;
                }
                // Use a regular synchronization: Narayana runs its afterCompletion after Agroal's interposed
                // synchronization releases the old connection. An interposed callback could exhaust a size-one pool.
                pending = new PendingFlush();
                pending.dataSourceNames.add(dataSourceName);
                transaction.registerSynchronization(pending);
                synchronizationRegistry.putResource(synchronizationKey, pending);
            } catch (RollbackException ex) {
                // The transaction became rollback-only after the status check; it has no committed work to flush.
            } catch (SystemException ex) {
                throw new IllegalStateException("Cannot register transaction cache invalidation", ex);
            }
        } finally {
            registrationLock.unlock();
        }
    }

    /** Flushes pending invalidations, either explicitly or through the optional scheduler integration. */
    public void retry() {
        flush(null);
    }

    private void flush(Set<String> dataSourceNames) {
        Throwable failure = null;
        for (InstanceHandle<TransactionCacheOperator> handle : operatorHandles) {
            if (!handle.getBean().isActive()) {
                continue;
            }
            if (dataSourceNames != null && handle.getBean().getQualifiers().stream()
                    .noneMatch(qualifier -> qualifier instanceof DataSource source && dataSourceNames.contains(source.value())
                            || qualifier instanceof Default
                                    && dataSourceNames.contains(DataSourceUtil.DEFAULT_DATASOURCE_NAME))) {
                continue;
            }
            try {
                flushOperator(handle);
            } catch (RuntimeException | Error ex) {
                if (failure == null) {
                    failure = ex;
                } else if (failure != ex) {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure instanceof RuntimeException ex) {
            throw ex;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void flushOperator(InstanceHandle<TransactionCacheOperator> handle) {
        // ArC intercepts self-invocation. Each datasource needs its own transaction, including scheduled retries.
        handle.get().flush();
    }

    private final class PendingFlush implements Synchronization {

        private final Set<String> dataSourceNames = new HashSet<>();

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            if (status == Status.STATUS_COMMITTED) {
                Set<String> sources;
                registrationLock.lock();
                try {
                    sources = Set.copyOf(dataSourceNames);
                } finally {
                    registrationLock.unlock();
                }
                try {
                    flush(sources);
                } catch (RuntimeException | Error ex) {
                    // A cache deletion failure cannot undo the business commit; its flush transaction rolls back.
                    LOGGER.warn("Transaction committed but cache invalidation failed; pending records require a later retry",
                            ex);
                }
            }
        }
    }
}
