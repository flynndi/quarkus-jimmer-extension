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

import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.DatabaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.agroal.DataSource;
import io.quarkus.arc.All;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.datasource.common.runtime.DataSourceUtil;
import io.quarkus.scheduler.Scheduled;

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

    public void onDatabaseEvent(@Observes DatabaseEvent event, EventMetadata metadata) {
        // Without a JTA transaction, later Jimmer listeners may not have written the invalidation record yet.
        // Periodic retry handles those records; a rollback-only transaction cannot schedule a successful commit.
        if (synchronizationRegistry.getTransactionStatus() != Status.STATUS_ACTIVE) {
            return;
        }
        String dataSourceName = metadata.getQualifiers().stream()
                .filter(DataSource.class::isInstance)
                .map(DataSource.class::cast)
                .map(DataSource::value)
                .findFirst().orElse(null);
        // Factory-published events carry their source. Unqualified application events cannot identify an operator.
        if (dataSourceName == null) {
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

    @Scheduled(every = "${quarkus.jimmer.transaction-cache-operator-fixed-delay}", identity = "jimmer.transaction-cache-operator-job")
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
                    LOGGER.warn("Transaction committed but cache invalidation failed; scheduled retry will retry it", ex);
                }
            }
        }
    }
}
