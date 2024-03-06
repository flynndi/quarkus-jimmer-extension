package io.quarkiverse.jimmer.it.config.transaction;

import jakarta.inject.Singleton;

import io.quarkiverse.jimmer.runtime.cache.impl.QuarkusTransactionCacheOperator;
import io.quarkus.agroal.DataSource;

@Singleton
public class QuarkusTransactionCacheOperatorConfig {

    @Singleton
    @DataSource("DB2")
    public QuarkusTransactionCacheOperator quarkusTransactionCacheOperator() {
        return new QuarkusTransactionCacheOperator();
    }
}
