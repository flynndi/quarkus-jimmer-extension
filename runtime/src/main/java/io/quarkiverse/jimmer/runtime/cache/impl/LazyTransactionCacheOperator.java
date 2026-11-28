package io.quarkiverse.jimmer.runtime.cache.impl;

import java.util.function.Supplier;

import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.cache.TransactionCacheOperator;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

public final class LazyTransactionCacheOperator extends TransactionCacheOperator {

    private final Supplier<JSqlClient> client;

    private volatile boolean initialized;

    public LazyTransactionCacheOperator(Supplier<JSqlClient> client) {
        this.client = client;
    }

    @Override
    protected void onInitialize(JSqlClientImplementor sqlClient) {
        super.onInitialize(sqlClient);
        initialized = true;
    }

    @Override
    public void flush() {
        // Accessing the ArC client proxy initializes the real client and its selected operator.
        // Resolve it only when flushing, after the operator bean has finished creation.
        client.get().getCaches();
        if (initialized) {
            super.flush();
        }
    }
}
