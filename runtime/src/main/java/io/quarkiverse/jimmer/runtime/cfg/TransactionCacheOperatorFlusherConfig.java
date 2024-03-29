package io.quarkiverse.jimmer.runtime.cfg;

import java.util.List;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkiverse.jimmer.runtime.cache.impl.QuarkusTransactionCacheOperator;
import io.quarkiverse.jimmer.runtime.cache.impl.TransactionCacheOperatorFlusher;
import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;

@Dependent
public class TransactionCacheOperatorFlusherConfig {

    @Produces
    @Singleton
    @DefaultBean
    public TransactionCacheOperatorFlusher transactionCacheOperatorFlusher(
            @All List<QuarkusTransactionCacheOperator> transactionCacheOperators) {
        return new TransactionCacheOperatorFlusher(transactionCacheOperators);
    }
}
