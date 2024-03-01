package io.quarkiverse.jimmer.it.config.transaction;

import io.quarkiverse.jimmer.runtime.cache.impl.QuarkusTransactionCacheOperator;
import jakarta.inject.Singleton;

@Singleton
public class QuarkusTransactionCacheOperatorConfig {

    //    @Singleton
    public QuarkusTransactionCacheOperator quarkusTransactionCacheOperator() {
        return new QuarkusTransactionCacheOperator();
    }
}
