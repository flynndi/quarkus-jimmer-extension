package io.quarkiverse.jimmer.test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;

import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;

/** Observes calls made by the real scheduled flusher without requiring an external cache. */
@Singleton
@Default
@io.quarkus.agroal.DataSource("<default>")
public class ScheduledRetryProbe extends TransactionCacheOperator {

    @Inject
    TransactionManager transactionManager;

    private final AtomicInteger calls = new AtomicInteger();
    private final Set<Integer> transactionStatuses = ConcurrentHashMap.newKeySet();
    private final CountDownLatch successfulRetry = new CountDownLatch(1);

    @Override
    public void flush() {
        try {
            transactionStatuses.add(transactionManager.getStatus());
        } catch (SystemException e) {
            throw new IllegalStateException(e);
        }
        if (calls.incrementAndGet() == 1) {
            throw new IllegalStateException("Simulated first scheduled cache flush failure");
        }
        successfulRetry.countDown();
    }

    boolean awaitSuccessfulRetry() throws InterruptedException {
        return successfulRetry.await(10, TimeUnit.SECONDS);
    }

    int calls() {
        return calls.get();
    }

    Set<Integer> transactionStatuses() {
        return Set.copyOf(transactionStatuses);
    }
}
