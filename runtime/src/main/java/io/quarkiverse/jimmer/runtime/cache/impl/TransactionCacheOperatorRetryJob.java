package io.quarkiverse.jimmer.runtime.cache.impl;

import jakarta.inject.Inject;

import io.quarkus.scheduler.Scheduled;

/** Registers periodic recovery only when the application supplies a Quarkus scheduler. */
public class TransactionCacheOperatorRetryJob {

    @Inject
    TransactionCacheOperatorFlusher flusher;

    @Scheduled(every = "${quarkus.jimmer.transaction-cache-operator-fixed-delay}", identity = "jimmer.transaction-cache-operator-job")
    void retry() {
        flusher.retry();
    }
}
