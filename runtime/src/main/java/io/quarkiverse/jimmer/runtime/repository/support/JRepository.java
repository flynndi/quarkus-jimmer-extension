package io.quarkiverse.jimmer.runtime.repository.support;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.GenericType;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.Selection;
import org.babyfish.jimmer.sql.ast.impl.mutation.Mutations;
import org.babyfish.jimmer.sql.ast.impl.query.FilterLevel;
import org.babyfish.jimmer.sql.ast.impl.query.MutableRootQueryImpl;
import org.babyfish.jimmer.sql.ast.impl.table.FetcherSelectionImpl;
import org.babyfish.jimmer.sql.ast.impl.table.TableImplementor;
import org.babyfish.jimmer.sql.ast.mutation.AffectedTable;
import org.babyfish.jimmer.sql.ast.mutation.BatchEntitySaveCommand;
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode;
import org.babyfish.jimmer.sql.ast.mutation.SimpleEntitySaveCommand;
import org.babyfish.jimmer.sql.ast.query.ConfigurableRootQuery;
import org.babyfish.jimmer.sql.ast.query.Order;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.fetcher.ViewMetadata;
import org.babyfish.jimmer.sql.runtime.ExecutionPurpose;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jboss.resteasy.reactive.common.util.types.Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.repository.QuarkusOrders;
import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkus.agroal.DataSource;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.impl.Reflections;

@Deprecated
public class JRepository<E, ID> implements io.quarkiverse.jimmer.runtime.repository.JRepository<E, ID> {

    protected final JSqlClientImplementor sqlClient = Utils.validateSqlClient(sql());

    public JSqlClient sql() {
        Class<?> actualType = ClientProxy.unwrap(this.getClass());
        if (null != actualType.getAnnotation(DataSource.class)) {
            return Jimmer.getJSqlClient(actualType.getAnnotation(DataSource.class).value());
        }
        return Jimmer.getDefaultJSqlClient();
    }

    public ImmutableType type() {
        Type[] types = Types.getActualTypeArgumentsOfAnInterface(this.getClass(),
                io.quarkiverse.jimmer.runtime.repository.JRepository.class);
        if (types.length == 2) {
            GenericType<Object> genericType = new GenericType<>(types[0]);
            return ImmutableType.get(genericType.getRawType());
        } else {
            throw new IllegalArgumentException(
                    "io.quarkiverse.jimmer.runtime.repository.support.JRepository<E, ID> 'E' illegality");
        }
    }

    public Class<E> entityType() {
        Type[] types = Types.getActualTypeArgumentsOfAnInterface(this.getClass(),
                io.quarkiverse.jimmer.runtime.repository.JRepository.class);
        if (types.length == 2) {
            return Reflections.getRawType(types[0]);
        } else {
            return null;
        }
    }

    @Override
    public E findNullable(ID id) {
        return sqlClient.getEntities().findById(entityType(), id);
    }

    @Override
    public E findNullable(ID id, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return findNullable(id);
        }
        return sqlClient.getEntities().findById(fetcher, id);
    }

    @Override
    public List<E> findByIds(Iterable<ID> ids) {
        return sqlClient.getEntities().findByIds(entityType(), Utils.toCollection(ids));
    }

    @Override
    public List<E> findByIds(Iterable<ID> ids, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return findByIds(ids);
        }
        return sqlClient.getEntities().findByIds(fetcher, Utils.toCollection(ids));
    }

    @Override
    public Map<ID, E> findMapByIds(Iterable<ID> ids) {
        return sqlClient.getEntities().findMapByIds(entityType(), Utils.toCollection(ids));
    }

    @Override
    public Map<ID, E> findMapByIds(Iterable<ID> ids, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return findMapByIds(ids);
        }
        return sqlClient.getEntities().findMapByIds(fetcher, Utils.toCollection(ids));
    }

    @NotNull
    @Override
    public List<E> findAll() {
        return createQuery(null, (Function<?, E>) null, null, null).execute();
    }

    @Override
    public List<E> findAll(TypedProp.Scalar<?, ?>... sortedProps) {
        return createQuery(null, (Function<?, E>) null, sortedProps, null).execute();
    }

    @Override
    public List<E> findAll(Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps) {
        return createQuery(fetcher, (Function<?, E>) null, sortedProps, null).execute();
    }

    @NotNull
    @Override
    public List<E> findAll(@NotNull Sort sort) {
        return createQuery(null, (Function<?, E>) null, null, sort).execute();
    }

    @Override
    public List<E> findAll(Fetcher<E> fetcher, Sort sort) {
        return createQuery(fetcher, (Function<?, E>) null, null, sort).execute();
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(int pageIndex, int pageSize) {
        return this.<E> createQuery(null, null, null, null)
                .fetchPage(pageIndex, pageSize);
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher) {
        return this.<E> createQuery(fetcher, null, null, null)
                .fetchPage(pageIndex, pageSize);
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps) {
        return this.<E> createQuery(null, null, sortedProps, null)
                .fetchPage(pageIndex, pageSize);
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher,
            TypedProp.Scalar<?, ?>... sortedProps) {
        return this.<E> createQuery(fetcher, null, sortedProps, null)
                .fetchPage(pageIndex, pageSize);
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(int pageIndex, int pageSize, Sort sort) {
        return this.<E> createQuery(null, null, null, sort)
                .fetchPage(pageIndex, pageSize);
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher, Sort sort) {
        return this.<E> createQuery(fetcher, null, null, sort)
                .fetchPage(pageIndex, pageSize);
    }

    @Override
    public org.babyfish.jimmer.@NotNull Page<E> findAll(@NotNull Pagination pagination) {
        return this.<E> createQuery(null, null, null, null)
                .fetchPage(pagination.index, pagination.size);
    }

    @Override
    public org.babyfish.jimmer.Page<E> findAll(Pagination pagination, Fetcher<E> fetcher) {
        return this.<E> createQuery(fetcher, null, null, null)
                .fetchPage(pagination.index, pagination.size);
    }

    @Override
    public long count() {
        return createQuery(null, null, null, null).fetchUnlimitedCount();
    }

    @NotNull
    @Override
    public SimpleEntitySaveCommand<E> saveCommand(@NotNull Input<E> input) {
        return sqlClient.getEntities().saveCommand(input);
    }

    @NotNull
    @Override
    public <S extends E> SimpleEntitySaveCommand<S> saveCommand(@NotNull S entity) {
        return sqlClient.getEntities().saveCommand(entity);
    }

    @NotNull
    @Override
    public <S extends E> BatchEntitySaveCommand<S> saveEntitiesCommand(@NotNull Iterable<S> entities) {
        return sqlClient
                .getEntities()
                .saveEntitiesCommand(Utils.toCollection(entities));
    }

    @Override
    public int delete(@NotNull E entity, DeleteMode mode) {
        return sqlClient.getEntities().delete(
                entityType(),
                ImmutableObjects.get(entity, type().getIdProp().getId()),
                mode).getAffectedRowCount(AffectedTable.of(type()));
    }

    @Override
    public int deleteAll(@NotNull Iterable<? extends E> entities, DeleteMode mode) {
        return sqlClient.getEntities().deleteAll(
                entityType(),
                Utils
                        .toCollection(entities)
                        .stream()
                        .map(it -> ImmutableObjects.get(it, type().getIdProp().getId()))
                        .collect(Collectors.toList()),
                mode).getAffectedRowCount(AffectedTable.of(type()));
    }

    @Override
    public int deleteById(@NotNull ID id, DeleteMode mode) {
        return sqlClient
                .getEntities()
                .delete(entityType(), id, mode)
                .getAffectedRowCount(AffectedTable.of(type()));
    }

    @Override
    public int deleteByIds(Iterable<? extends ID> ids, DeleteMode mode) {
        return sqlClient
                .getEntities()
                .deleteAll(entityType(), Utils.toCollection(ids), mode)
                .getAffectedRowCount(AffectedTable.of(type()));
    }

    @Override
    public void deleteAll() {
        Mutations
                .createDelete(sqlClient, type(), (d, t) -> {
                })
                .execute();
    }

    @Override
    public <V extends View<E>> Viewer<E, ID, V> viewer(Class<V> viewType) {
        return new ViewerImpl<>(viewType);
    }

    @SuppressWarnings("unchecked")
    private <X> ConfigurableRootQuery<?, X> createQuery(
            Fetcher<?> fetcher,
            @Nullable Function<?, X> converter,
            @Nullable TypedProp.Scalar<?, ?>[] sortedProps,
            @Nullable Sort sort) {
        MutableRootQueryImpl<Table<?>> query = new MutableRootQueryImpl<>(sqlClient, type(), ExecutionPurpose.QUERY,
                FilterLevel.DEFAULT);
        TableImplementor<?> table = query.getTableImplementor();
        if (sortedProps != null) {
            for (TypedProp.Scalar<?, ?> sortedProp : sortedProps) {
                if (!sortedProp.unwrap().getDeclaringType().isAssignableFrom(type())) {
                    throw new IllegalArgumentException(
                            "The sorted field \"" +
                                    sortedProp +
                                    "\" does not belong to the type \"" +
                                    type() +
                                    "\" or its super types");
                }
                PropExpression<?> expr = table.get(sortedProp.unwrap());
                Order astOrder;
                if (sortedProp.isDesc()) {
                    astOrder = expr.desc();
                } else {
                    astOrder = expr.asc();
                }
                if (sortedProp.isNullsFirst()) {
                    astOrder = astOrder.nullsFirst();
                }
                if (sortedProp.isNullsLast()) {
                    astOrder = astOrder.nullsLast();
                }
                query.orderBy(astOrder);
            }
        }
        if (sort != null) {
            query.orderBy(QuarkusOrders.toOrders(table, sort));
        }
        return query.select(
                fetcher != null ? new FetcherSelectionImpl<>(table, fetcher, converter) : (Selection<X>) table);
    }

    private class ViewerImpl<V extends View<E>> implements Viewer<E, ID, V> {

        private final Class<V> viewType;

        private final ViewMetadata<E, V> metadata;

        private ViewerImpl(Class<V> viewType) {
            this.viewType = viewType;
            this.metadata = ViewMetadata.of(viewType);
        }

        @Override
        public V findNullable(ID id) {
            return sqlClient.getEntities().findById(viewType, id);
        }

        @Override
        public List<V> findByIds(Iterable<ID> ids) {
            return sqlClient.getEntities().findByIds(viewType, Utils.toCollection(ids));
        }

        @Override
        public Map<ID, V> findMapByIds(Iterable<ID> ids) {
            return sqlClient.getEntities().findMapByIds(viewType, Utils.toCollection(ids));
        }

        @Override
        public List<V> findAll() {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), null, null).execute();
        }

        @Override
        public List<V> findAll(TypedProp.Scalar<?, ?>... sortedProps) {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), sortedProps, null).execute();
        }

        @Override
        public List<V> findAll(Sort sort) {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), null, sort).execute();
        }

        @Override
        public org.babyfish.jimmer.Page<V> findAll(Pagination pagination) {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), null, null)
                    .fetchPage(pagination.index, pagination.size);
        }

        @Override
        public org.babyfish.jimmer.Page<V> findAll(int pageIndex, int pageSize) {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), null, null)
                    .fetchPage(pageIndex, pageSize);
        }

        @Override
        public org.babyfish.jimmer.Page<V> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps) {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), sortedProps, null)
                    .fetchPage(pageIndex, pageSize);
        }

        @Override
        public org.babyfish.jimmer.Page<V> findAll(int pageIndex, int pageSize, Sort sort) {
            return createQuery(metadata.getFetcher(), metadata.getConverter(), null, sort)
                    .fetchPage(pageIndex, pageSize);
        }
    }
}
