package io.quarkiverse.jimmer.runtime.repository;

import java.util.ArrayList;
import java.util.List;

import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.StringExpression;
import org.babyfish.jimmer.sql.ast.query.Order;
import org.babyfish.jimmer.sql.ast.table.Props;

import io.quarkiverse.jimmer.runtime.repository.common.Sort;

/**
 * Adapter retained for legacy repository sorting.
 *
 * @deprecated New repositories use Jimmer typed properties, order expressions, or the Kotlin sort DSL.
 */
@Deprecated(forRemoval = false)
public class QuarkusOrders {

    private static final TypedProp.Scalar<?, ?>[] EMPTY_PROPS = new TypedProp.Scalar<?, ?>[0];

    private static final Order[] EMPTY_ORDERS = new Order[0];

    private QuarkusOrders() {
    }

    public static Order[] toOrders(Props table, Sort sort) {
        if (sort == null || sort.isEmpty()) {
            return EMPTY_ORDERS;
        }
        List<Order> astOrders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Expression<?> expr = Order.orderedExpression(table, order.getProperty());
            if (order.isIgnoreCase() && expr instanceof StringExpression) {
                expr = ((StringExpression) expr).lower();
            }
            Order astOrder = order.isDescending() ? expr.desc() : expr.asc();
            switch (order.getNullHandling()) {
                case NULLS_FIRST:
                    astOrder = astOrder.nullsFirst();
                    break;
                case NULLS_LAST:
                    astOrder = astOrder.nullsLast();
                    break;
            }
            astOrders.add(astOrder);
        }
        return astOrders.toArray(EMPTY_ORDERS);
    }
}
