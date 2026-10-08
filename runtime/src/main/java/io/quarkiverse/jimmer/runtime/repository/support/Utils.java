package io.quarkiverse.jimmer.runtime.repository.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.query.Order;
import org.babyfish.jimmer.sql.ast.query.OrderMode;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.ast.table.spi.PropExpressionImplementor;
import org.babyfish.jimmer.sql.ast.table.spi.TableProxy;
import org.babyfish.jimmer.sql.meta.EmbeddedColumns;
import org.babyfish.jimmer.sql.meta.MetadataStrategy;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;

import io.quarkiverse.jimmer.runtime.repository.common.Sort;

/**
 * Internal utilities for legacy repository implementations, not an application extension SPI.
 * Existing public methods are retained for compatibility.
 */
public class Utils {

    private Utils() {
    }

    public static <E> Collection<E> toCollection(Iterable<E> iterable) {
        if (iterable instanceof Collection<?>) {
            return (Collection<E>) iterable;
        }
        if (iterable == null) {
            return Collections.emptyList();
        }
        List<E> list = new ArrayList<>();
        for (E e : iterable) {
            list.add(e);
        }
        return list;
    }

    public static JSqlClientImplementor validateSqlClient(JSqlClient sqlClient) {
        Objects.requireNonNull(sqlClient, "sqlClient is required");
        // Calling the CDI proxy here initializes and validates the client before application schema setup can run.
        // Repositories require this interface, but do not require specific connection or resolver providers.
        if (sqlClient instanceof JSqlClientImplementor implementor) {
            return implementor;
        }
        throw new IllegalArgumentException("The sql client must implement \"" + JSqlClientImplementor.class.getName() + "\"");
    }

    public static Sort toSort(List<Order> orders, MetadataStrategy strategy) {
        if (orders == null || orders.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> quarkusOrders = new ArrayList<>(orders.size());
        for (Order order : orders) {
            if (order.getExpression() instanceof PropExpression<?>) {
                PropExpressionImplementor<?> propExpr = (PropExpressionImplementor<?>) order.getExpression();
                String prefix = prefix(propExpr.getTable());
                EmbeddedColumns.Partial partial = propExpr.getPartial(strategy);
                String path = partial != null ? partial.path() : propExpr.getProp().getName();
                if (prefix != null) {
                    path = prefix + '.' + path;
                }
                Sort.NullHandling nullHandling;
                switch (order.getNullOrderMode()) {
                    case NULLS_FIRST:
                        nullHandling = Sort.NullHandling.NULLS_FIRST;
                        break;
                    case NULLS_LAST:
                        nullHandling = Sort.NullHandling.NULLS_LAST;
                        break;
                    default:
                        nullHandling = Sort.NullHandling.NATIVE;
                        break;
                }
                quarkusOrders.add(
                        new Sort.Order(
                                order.getOrderMode() == OrderMode.DESC ? Sort.Direction.DESC : Sort.Direction.ASC,
                                path,
                                nullHandling));
            }
        }
        return Sort.by(quarkusOrders);
    }

    private static String prefix(Table<?> table) {
        ImmutableProp prop = table instanceof TableProxy<?> ? ((TableProxy<?>) table).__prop()
                : ((TableImplementor<?>) table).getJoinProp();
        if (prop == null) {
            return null;
        }

        String name = prop.getName();

        boolean inverse = table instanceof TableProxy<?> ? ((TableProxy<?>) table).__isInverse()
                : ((TableImplementor<?>) table).isInverse();
        if (inverse) {
            name = "`←" + name + '`';
        }

        Table<?> parent = table instanceof TableProxy<?> ? ((TableProxy<?>) table).__parent()
                : ((TableImplementor<?>) table).getParent();
        if (parent == null) {
            return name;
        }
        String parentPrefix = prefix(parent);
        if (parentPrefix == null) {
            return name;
        }
        return parentPrefix + '.' + name;
    }
}
