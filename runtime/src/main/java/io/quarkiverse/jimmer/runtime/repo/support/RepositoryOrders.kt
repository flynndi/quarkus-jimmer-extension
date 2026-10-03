package io.quarkiverse.jimmer.runtime.repo.support

import org.babyfish.jimmer.meta.NullOrderMode
import org.babyfish.jimmer.sql.ast.query.OrderMode
import org.babyfish.jimmer.sql.kt.ast.expression.KPropExpression
import org.babyfish.jimmer.sql.kt.ast.expression.asc
import org.babyfish.jimmer.sql.kt.ast.expression.desc
import org.babyfish.jimmer.sql.kt.ast.query.KMutableQuery
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import org.babyfish.jimmer.sql.kt.ast.table.KNonNullTable

/** Applies Jimmer's typed sorting DSL without depending on the legacy repository API. */
fun <E : Any> KMutableQuery<*>.orderBy(block: (SortDsl<E>.() -> Unit)?) {
    if (block == null) {
        return
    }
    val orders = mutableListOf<SortDsl.Order>()
    block(SortDsl(orders))
    for (order in orders) {
        val expression: KPropExpression<Any> = (table as KNonNullTable<*>).get(order.prop.name)
        val astOrder = if (order.mode == OrderMode.DESC) expression.desc() else expression.asc()
        orderBy(
            when (order.nullOrderMode) {
                NullOrderMode.NULLS_FIRST -> astOrder.nullsFirst()
                NullOrderMode.NULLS_LAST -> astOrder.nullsLast()
                else -> astOrder
            }
        )
    }
}
