package io.quarkiverse.jimmer.runtime.graphql

import graphql.schema.DataFetchingEnvironment
import org.babyfish.jimmer.sql.fetcher.Fetcher

inline fun <reified E: Any> DataFetchingEnvironment.toFetcher(): Fetcher<E> =
    DataFetchingEnvironments.createFetcher(
        E::class.java,
        this
    )
