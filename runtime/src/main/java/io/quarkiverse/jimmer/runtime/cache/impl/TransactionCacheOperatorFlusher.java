package io.quarkiverse.jimmer.runtime.cache.impl;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.event.DatabaseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.All;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class TransactionCacheOperatorFlusher {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionCacheOperatorFlusher.class);

    private final List<TransactionCacheOperator> operators;

    private final ThreadLocal<Boolean> dirtyLocal = new ThreadLocal<>();

    public TransactionCacheOperatorFlusher(@All List<TransactionCacheOperator> operators) {
        if (operators.isEmpty()) {
            throw new IllegalArgumentException("`operators` cannot be empty");
        }
        this.operators = operators;
    }

    public void beforeCommit(@Observes(during = TransactionPhase.IN_PROGRESS) DatabaseEvent e) {
        dirtyLocal.set(Boolean.TRUE);
    }

    public void afterCommit(@Observes(during = TransactionPhase.AFTER_COMPLETION) DatabaseEvent e) {
        if (dirtyLocal.get() != null) {
            dirtyLocal.remove();
            flush();
        }
    }

    @Scheduled(every = "${quarkus.jimmer.transaction-cache-operator-fixed-delay}", identity = "jimmer.transaction-cache-operator-job")
    public void retry() {
        flush();
    }

    private void flush() {
        if (operators.size() == 1) {
            TransactionCacheOperator operator = operators.get(0);
            operator.flush();
        } else {
            Throwable throwable = null;
            for (TransactionCacheOperator operator : operators) {
                try {
                    operator.flush();
                } catch (RuntimeException | Error ex) {
                    if (throwable == null) {
                        throwable = ex;
                    }
                }
            }
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            if (throwable != null) {
                throw (Error) throwable;
            }
        }
    }
}
