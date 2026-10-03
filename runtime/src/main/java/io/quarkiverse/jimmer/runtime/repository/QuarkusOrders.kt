package io.quarkiverse.jimmer.runtime.repository

import io.quarkiverse.jimmer.runtime.repository.common.Sort
import io.quarkiverse.jimmer.runtime.repo.support.orderBy as repositoryOrderBy
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import org.babyfish.jimmer.sql.kt.ast.query.KMutableQuery
import org.babyfish.jimmer.sql.kt.ast.table.impl.KTableImplementor

@Deprecated("Retained for legacy Sort parameters. New queries should use Jimmer ordering expressions or the sort DSL.")
fun KMutableQuery<*>.orderBy(sort: Sort?) {
    orderBy(*QuarkusOrders.toOrders((table as KTableImplementor<*>).javaTable, sort))
}

/** Compatibility bridge preserving the original QuarkusOrdersKt JVM entry point. */
@Deprecated("Retained for compatibility. Use typed sorting on KotlinRepository or Jimmer query ordering in new code.")
fun <E: Any> KMutableQuery<*>.orderBy(block: (SortDsl<E>.() -> Unit)?) {
    repositoryOrderBy(block)
}

@Deprecated("Retained for legacy Sort parameters. New queries should use Jimmer ordering expressions or the sort DSL.")
fun KMutableQuery<*>.orderByIf(condition: Boolean, sort: Sort?) {
    if (condition) {
        orderBy(sort)
    }
}
