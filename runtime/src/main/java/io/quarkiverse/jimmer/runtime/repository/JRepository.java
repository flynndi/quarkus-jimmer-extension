package io.quarkiverse.jimmer.runtime.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.babyfish.jimmer.ImmutableObjects;
import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.impl.util.CollectionUtils;
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
import org.babyfish.jimmer.sql.ast.mutation.*;
import org.babyfish.jimmer.sql.ast.query.ConfigurableRootQuery;
import org.babyfish.jimmer.sql.ast.query.Order;
import org.babyfish.jimmer.sql.ast.table.Table;
import org.babyfish.jimmer.sql.fetcher.DtoMetadata;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.ExecutionPurpose;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.quarkiverse.jimmer.runtime.Jimmer;
import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.runtime.repository.support.JpaOperationsData;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;
import io.quarkiverse.jimmer.runtime.repository.support.Utils;
import io.quarkus.agroal.DataSource;

public interface JRepository<E, ID> {

    /*
     * For provider
     */
    private JSqlClientImplementor sqlClient() {
        return Utils.validateSqlClient(sql());
    }

    default JSqlClient sql() {
        DataSource dataSource = this.getClass().getAnnotation(DataSource.class);
        return dataSource == null ? Jimmer.getDefaultJSqlClient() : Jimmer.getJSqlClient(dataSource.value());
    }

    default ImmutableType type() {
        return JpaOperationsData.getImmutableType(this.getClass());
    }

    @SuppressWarnings("unchecked")
    default Class<E> entityType() {
        return (Class<E>) JpaOperationsData.getEntityClass(this.getClass());
    }

    /*
     * For consumer
     */
    default E findNullable(ID id) {
        return sqlClient().getEntities().findById(entityType(), id);
    }

    default E findNullable(ID id, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return findNullable(id);
        }
        return sqlClient().getEntities().findById(fetcher, id);
    }

    @NotNull
    default Optional<E> findById(@NotNull ID id) {
        return Optional.ofNullable(findNullable(id));
    }

    @NotNull
    default Optional<E> findById(ID id, Fetcher<E> fetcher) {
        return Optional.ofNullable(findNullable(id, fetcher));
    }

    default List<E> findByIds(Iterable<ID> ids) {
        return sqlClient().getEntities().findByIds(entityType(), Utils.toCollection(ids));
    }

    @NotNull
    default List<E> findAllById(@NotNull Iterable<ID> ids) {
        return findByIds(ids);
    }

    default List<E> findByIds(Iterable<ID> ids, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return findByIds(ids);
        }
        return sqlClient().getEntities().findByIds(fetcher, Utils.toCollection(ids));
    }

    default Map<ID, E> findMapByIds(Iterable<ID> ids) {
        return sqlClient().getEntities().findMapByIds(entityType(), Utils.toCollection(ids));
    }

    default Map<ID, E> findMapByIds(Iterable<ID> ids, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return findMapByIds(ids);
        }
        return sqlClient().getEntities().findMapByIds(fetcher, Utils.toCollection(ids));
    }

    @NotNull
    default List<E> findAll() {
        return createQuery(null, (Function<?, E>) null, null, null).execute();
    }

    default List<E> findAll(TypedProp.Scalar<?, ?>... sortedProps) {
        return createQuery(null, (Function<?, E>) null, sortedProps, null).execute();
    }

    default List<E> findAll(Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps) {
        return createQuery(fetcher, (Function<?, E>) null, sortedProps, null).execute();
    }

    @NotNull
    default List<E> findAll(@NotNull Sort sort) {
        return createQuery(null, (Function<?, E>) null, null, sort).execute();
    }

    default List<E> findAll(Fetcher<E> fetcher, Sort sort) {
        return createQuery(fetcher, (Function<?, E>) null, null, sort).execute();
    }

    default Page<E> findAll(int pageIndex, int pageSize) {
        return this.<E> createQuery(null, null, null, null)
                .fetchPage(pageIndex, pageSize);
    }

    default Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher) {
        return this.<E> createQuery(fetcher, null, null, null)
                .fetchPage(pageIndex, pageSize);
    }

    default Page<E> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps) {
        return this.<E> createQuery(null, null, sortedProps, null)
                .fetchPage(pageIndex, pageSize);
    }

    default Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps) {
        return this.<E> createQuery(fetcher, null, sortedProps, null)
                .fetchPage(pageIndex, pageSize);
    }

    default Page<E> findAll(int pageIndex, int pageSize, Sort sort) {
        return this.<E> createQuery(null, null, null, sort)
                .fetchPage(pageIndex, pageSize);
    }

    default Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher, Sort sort) {
        return this.<E> createQuery(fetcher, null, null, sort)
                .fetchPage(pageIndex, pageSize);
    }

    @NotNull
    default Page<E> findAll(@NotNull Pagination pagination) {
        return this.<E> createQuery(null, null, null, null)
                .fetchPage(pagination.index, pagination.size);
    }

    default Page<E> findAll(Pagination pagination, Fetcher<E> fetcher) {
        return this.<E> createQuery(fetcher, null, null, null)
                .fetchPage(pagination.index, pagination.size);
    }

    default boolean existsById(@NotNull ID id) {
        return findNullable(id) != null;
    }

    default long count() {
        return createQuery(null, null, null, null).fetchUnlimitedCount();
    }

    default E insert(@NotNull E entity) {
        return save(entity, SaveMode.INSERT_ONLY).getModifiedEntity();
    }

    @NotNull
    default E insert(@NotNull Input<E> input) {
        return save(input.toEntity(), SaveMode.INSERT_ONLY).getModifiedEntity();
    }

    @NotNull
    default E update(@NotNull E entity) {
        return save(entity, SaveMode.UPDATE_ONLY).getModifiedEntity();
    }

    @NotNull
    default E update(@NotNull Input<E> input) {
        return save(input.toEntity(), SaveMode.UPDATE_ONLY).getModifiedEntity();
    }

    @NotNull
    default <S extends E> S save(@NotNull S entity) {
        return saveCommand(entity).execute().getModifiedEntity();
    }

    @NotNull
    default E save(@NotNull Input<E> input) {
        return saveCommand(input.toEntity()).execute().getModifiedEntity();
    }

    @NotNull
    default <S extends E> SimpleSaveResult<S> save(@NotNull S entity, SaveMode mode) {
        return saveCommand(entity).setMode(mode).execute();
    }

    @NotNull
    default SimpleSaveResult<E> save(@NotNull Input<E> input, SaveMode mode) {
        return saveCommand(input.toEntity()).setMode(mode).execute();
    }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     */
    default <S extends E> SimpleSaveResult<S> merge(@NotNull S entity) {
        return saveCommand(entity).setAssociatedModeAll(AssociatedSaveMode.MERGE).execute();
    }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     */
    default SimpleSaveResult<E> merge(@NotNull Input<E> input) {
        return saveCommand(input.toEntity()).setAssociatedModeAll(AssociatedSaveMode.MERGE).execute();
    }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     */
    default <S extends E> SimpleSaveResult<S> merge(@NotNull S entity, SaveMode mode) {
        return saveCommand(entity).setAssociatedModeAll(AssociatedSaveMode.MERGE).setMode(mode).execute();
    }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     */
    default SimpleSaveResult<E> merge(@NotNull Input<E> input, SaveMode mode) {
        return saveCommand(input.toEntity()).setAssociatedModeAll(AssociatedSaveMode.MERGE).setMode(mode).execute();
    }

    /**
     * For associated objects, only insert operations are executed.
     */
    default <S extends E> SimpleSaveResult<S> append(@NotNull S entity) {
        return saveCommand(entity).setAssociatedModeAll(AssociatedSaveMode.APPEND).execute();
    }

    /**
     * For associated objects, only insert operations are executed.
     */
    default SimpleSaveResult<E> append(@NotNull Input<E> input) {
        return saveCommand(input.toEntity()).setAssociatedModeAll(AssociatedSaveMode.APPEND).execute();
    }

    /**
     * For associated objects, only insert operations are executed.
     */
    default <S extends E> SimpleSaveResult<S> append(@NotNull S entity, SaveMode mode) {
        return saveCommand(entity).setAssociatedModeAll(AssociatedSaveMode.APPEND).setMode(mode).execute();
    }

    /**
     * For associated objects, only insert operations are executed.
     */
    default SimpleSaveResult<E> append(@NotNull Input<E> input, SaveMode mode) {
        return saveCommand(input.toEntity()).setAssociatedModeAll(AssociatedSaveMode.APPEND).setMode(mode).execute();
    }

    @NotNull
    default SimpleEntitySaveCommand<E> saveCommand(@NotNull Input<E> input) {
        return sqlClient().getEntities().saveCommand(input);
    }

    @NotNull
    default <S extends E> SimpleEntitySaveCommand<S> saveCommand(@NotNull S entity) {
        return sqlClient().getEntities().saveCommand(entity);
    }

    /**
     * Replaced by saveEntities, will be removed in 1.0
     */
    @Deprecated
    default <S extends E> Iterable<S> saveAll(Iterable<S> entities) {
        return saveEntities(entities);
    }

    @NotNull
    default <S extends E> Iterable<S> saveEntities(@NotNull Iterable<S> entities) {
        return saveEntitiesCommand(Utils.toCollection(entities))
                .execute()
                .getSimpleResults()
                .stream()
                .map(SimpleSaveResult::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> BatchSaveResult<S> saveEntities(@NotNull Iterable<S> entities, SaveMode mode) {
        return saveEntitiesCommand(Utils.toCollection(entities))
                .setMode(mode)
                .execute();
    }

    @NotNull
    default <S extends E> BatchEntitySaveCommand<S> saveEntitiesCommand(@NotNull Iterable<S> entities) {
        return sqlClient()
                .getEntities()
                .saveEntitiesCommand(Utils.toCollection(entities));
    }

    @NotNull
    default <S extends E> BatchEntitySaveCommand<S> saveInputsCommand(@NotNull Iterable<Input<S>> inputs) {
        return saveEntitiesCommand(CollectionUtils.map(inputs, Input::toEntity));
    }

    default void delete(@NotNull E entity) {
        delete(entity, DeleteMode.AUTO);
    }

    default int delete(@NotNull E entity, DeleteMode mode) {
        return sqlClient().getEntities().delete(
                entityType(),
                ImmutableObjects.get(entity, type().getIdProp().getId()),
                mode).getAffectedRowCount(AffectedTable.of(type()));
    }

    default void deleteAll(@NotNull Iterable<? extends E> entities) {
        deleteAll(entities, DeleteMode.AUTO);
    }

    default int deleteAll(@NotNull Iterable<? extends E> entities, DeleteMode mode) {
        return sqlClient().getEntities().deleteAll(
                entityType(),
                Utils
                        .toCollection(entities)
                        .stream()
                        .map(it -> ImmutableObjects.get(it, type().getIdProp().getId()))
                        .collect(Collectors.toList()),
                mode).getAffectedRowCount(AffectedTable.of(type()));
    }

    default void deleteById(@NotNull ID id) {
        deleteById(id, DeleteMode.AUTO);
    }

    default int deleteById(@NotNull ID id, DeleteMode mode) {
        return sqlClient()
                .getEntities()
                .delete(entityType(), id, mode)
                .getAffectedRowCount(AffectedTable.of(type()));
    }

    default void deleteByIds(Iterable<? extends ID> ids) {
        deleteByIds(ids, DeleteMode.AUTO);
    }

    default void deleteAllById(@NotNull Iterable<? extends ID> ids) {
        deleteByIds(ids, DeleteMode.AUTO);
    }

    default int deleteByIds(Iterable<? extends ID> ids, DeleteMode mode) {
        return sqlClient()
                .getEntities()
                .deleteAll(entityType(), Utils.toCollection(ids), mode)
                .getAffectedRowCount(AffectedTable.of(type()));
    }

    default void deleteAll() {
        Mutations
                .createDelete(sqlClient(), type(), (d, t) -> {
                })
                .execute();
    }

    default <V extends View<E>> Viewer<E, ID, V> viewer(Class<V> viewType) {

        return new Viewer<E, ID, V>() {

            private final DtoMetadata<E, V> metadata = DtoMetadata.of(viewType);

            @Override
            public V findNullable(ID id) {
                return sqlClient().getEntities().findById(viewType, id);
            }

            @Override
            public List<V> findByIds(Iterable<ID> ids) {
                return sqlClient().getEntities().findByIds(viewType, Utils.toCollection(ids));
            }

            @Override
            public Map<ID, V> findMapByIds(Iterable<ID> ids) {
                return sqlClient().getEntities().findMapByIds(viewType, Utils.toCollection(ids));
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
            public Page<V> findAll(Pagination pagination) {
                return createQuery(metadata.getFetcher(), metadata.getConverter(), null, null)
                        .fetchPage(pagination.index, pagination.size);
            }

            @Override
            public Page<V> findAll(int pageIndex, int pageSize) {
                return createQuery(metadata.getFetcher(), metadata.getConverter(), null, null)
                        .fetchPage(pageIndex, pageSize);
            }

            @Override
            public Page<V> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps) {
                return createQuery(metadata.getFetcher(), metadata.getConverter(), sortedProps, null)
                        .fetchPage(pageIndex, pageSize);
            }

            @Override
            public Page<V> findAll(int pageIndex, int pageSize, Sort sort) {
                return createQuery(metadata.getFetcher(), metadata.getConverter(), null, sort)
                        .fetchPage(pageIndex, pageSize);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <X> ConfigurableRootQuery<?, X> createQuery(
            Fetcher<?> fetcher,
            @Nullable Function<?, X> converter,
            @Nullable TypedProp.Scalar<?, ?>[] sortedProps,
            @Nullable Sort sort) {
        MutableRootQueryImpl<Table<?>> query = new MutableRootQueryImpl<>(sqlClient(), type(), ExecutionPurpose.QUERY,
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

    interface Viewer<E, ID, V extends View<E>> {

        V findNullable(ID id);

        List<V> findByIds(Iterable<ID> ids);

        Map<ID, V> findMapByIds(Iterable<ID> ids);

        List<V> findAll();

        List<V> findAll(TypedProp.Scalar<?, ?>... sortedProps);

        List<V> findAll(Sort sort);

        Page<V> findAll(Pagination pagination);

        Page<V> findAll(int pageIndex, int pageSize);

        Page<V> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps);

        Page<V> findAll(int pageIndex, int pageSize, Sort sort);
    }
}
