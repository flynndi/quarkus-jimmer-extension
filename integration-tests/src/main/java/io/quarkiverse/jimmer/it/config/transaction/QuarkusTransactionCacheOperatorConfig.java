package io.quarkiverse.jimmer.it.config.transaction;

import jakarta.inject.Singleton;

import io.quarkiverse.jimmer.runtime.cache.impl.QuarkusTransactionCacheOperator;

@Singleton
public class QuarkusTransactionCacheOperatorConfig {

    //    @Singleton
    public QuarkusTransactionCacheOperator quarkusTransactionCacheOperator() {
        return new QuarkusTransactionCacheOperator();
    }
}
