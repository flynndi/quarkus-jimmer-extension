package io.quarkiverse.jimmer.runtime.transaction;

import java.util.function.Supplier;

import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

import org.babyfish.jimmer.sql.transaction.Propagation;

/** Internal CDI boundary for synchronous Jimmer JDBC transactions; not an application extension point. */
@Singleton
public class QuarkusTransactionExecutor {

    public <R> R execute(Propagation propagation, Supplier<R> action) {
        // ArC intercepts self-invocation of these non-private methods, so Narayana owns all six propagation modes.
        Result<R> result = switch (propagation) {
            case REQUIRED -> required(action);
            case REQUIRES_NEW -> requiresNew(action);
            case SUPPORTS -> supports(action);
            case NOT_SUPPORTED -> notSupported(action);
            case MANDATORY -> mandatory(action);
            case NEVER -> never(action);
        };
        return result.value();
    }

    @Transactional(Transactional.TxType.REQUIRED)
    <R> Result<R> required(Supplier<R> action) {
        return new Result<>(action.get());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    <R> Result<R> requiresNew(Supplier<R> action) {
        return new Result<>(action.get());
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    <R> Result<R> supports(Supplier<R> action) {
        return new Result<>(action.get());
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    <R> Result<R> notSupported(Supplier<R> action) {
        return new Result<>(action.get());
    }

    @Transactional(Transactional.TxType.MANDATORY)
    <R> Result<R> mandatory(Supplier<R> action) {
        return new Result<>(action.get());
    }

    @Transactional(Transactional.TxType.NEVER)
    <R> Result<R> never(Supplier<R> action) {
        return new Result<>(action.get());
    }

    // A future/publisher returned as a value must not turn this synchronous JDBC boundary into a reactive transaction.
    record Result<R>(R value) {
    }
}
