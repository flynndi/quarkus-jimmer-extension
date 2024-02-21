package io.quarkiverse.jimmer.it.config.transaction;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import io.quarkus.arc.All;
import io.quarkus.arc.DefaultBean;

@ApplicationScoped
public class QuarkusTransactionCacheOperatorFlusherConfig {

    @Produces
    @Singleton
    @DefaultBean
    public QuarkusTransactionCacheOperatorFlusher transactionCacheOperatorFlusher(
            @All List<QuarkusTransactionCacheOperator> transactionCacheOperators) {
        return new QuarkusTransactionCacheOperatorFlusher(transactionCacheOperators);
    }
}
