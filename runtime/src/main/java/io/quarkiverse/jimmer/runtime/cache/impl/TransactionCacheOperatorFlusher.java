package io.quarkiverse.jimmer.runtime.cache.impl;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;

import org.babyfish.jimmer.sql.event.DatabaseEvent;

import io.quarkus.arc.All;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class TransactionCacheOperatorFlusher {

    private final List<QuarkusTransactionCacheOperator> operators;

    private final ThreadLocal<Boolean> dirtyLocal = new ThreadLocal<>();

    public TransactionCacheOperatorFlusher(@All List<QuarkusTransactionCacheOperator> operators) {
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

    @Scheduled(every = "5s", identity = "jimmer-trans-cache-operator-job")
    public void retry() {
        flush();
    }

    private void flush() {
        if (operators.size() == 1) {
            QuarkusTransactionCacheOperator operator = operators.get(0);
            operator.flush();
        } else {
            Throwable throwable = null;
            for (QuarkusTransactionCacheOperator operator : operators) {
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
