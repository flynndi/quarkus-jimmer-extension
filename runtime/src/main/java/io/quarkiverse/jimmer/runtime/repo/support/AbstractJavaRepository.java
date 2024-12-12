package io.quarkiverse.jimmer.runtime.repo.support;

import java.util.*;
import java.util.function.Function;

import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.Slice;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.PropId;
import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.runtime.ImmutableSpi;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.PropExpression;
import org.babyfish.jimmer.sql.ast.Selection;
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

import io.quarkiverse.jimmer.runtime.repo.JavaRepository;
import io.quarkiverse.jimmer.runtime.repo.PageParam;
import io.quarkiverse.jimmer.runtime.repository.support.JpaOperationsData;

/**
 * Base implementation of {@link JavaRepository}
 *
 * @param <E> The entity type
 * @param <ID> The entity id type
 */
public class AbstractJavaRepository<E, ID> implements JavaRepository<E, ID> {

    protected final JSqlClient sql;

    protected final Class<E> entityType;

    protected final ImmutableType type;

    @SuppressWarnings("unchecked")
    public AbstractJavaRepository(JSqlClient sql) {
        this.sql = Objects.requireNonNull(sql, "sqlClient is required");
        this.entityType = (Class<E>) JpaOperationsData.getEntityClass(this.getClass());
        this.type = ImmutableType.get(entityType);
        if (!type.isEntity()) {
            throw new IllegalArgumentException(
                    "\"" +
                            entityType +
                            "\" is not entity type decorated by @" +
                            Entity.class.getName());
        }
    }

    @Nullable
    @Override
    public E findById(ID id, @Nullable Fetcher<E> fetcher) {
        if (fetcher == null) {
            return sql.findById(entityType, id);
        }
        return sql.findById(fetcher, id);
    }

    @Nullable
    @Override
    public <V extends View<E>> V findById(ID id, Class<V> viewType) {
        return sql.findById(viewType, id);
    }

    @NotNull
    @Override
    public List<E> findByIds(Iterable<ID> ids, @Nullable Fetcher<E> fetcher) {
        if (fetcher == null) {
            return sql.findByIds(entityType, ids);
        }
        return sql.findByIds(fetcher, ids);
    }

    @NotNull
    @Override
    public <V extends View<E>> List<V> findByIds(Iterable<ID> ids, Class<V> viewType) {
        return sql.findByIds(viewType, ids);
    }

    @NotNull
    @Override
    public Map<ID, E> findMapByIds(Iterable<ID> ids, Fetcher<E> fetcher) {
        if (fetcher == null) {
            return sql.findMapByIds(entityType, ids);
        }
        return sql.findMapByIds(fetcher, ids);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    public <V extends View<E>> Map<ID, V> findMapByIds(Iterable<ID> ids, Class<V> viewType) {
        DtoMetadata<E, V> metadata = DtoMetadata.of(viewType);
        List<E> entities = sql.findByIds(metadata.getFetcher(), ids);
        Map<ID, V> map = new LinkedHashMap<>((entities.size() * 4 + 2) / 3);
        PropId idPropId = type.getIdProp().getId();
        for (E entity : entities) {
            map.put(
                    (ID) ((ImmutableSpi) entity).__get(idPropId),
                    metadata.getConverter().apply(entity));
        }
        return map;
    }

    @NotNull
    @Override
    public List<E> findAll(@Nullable Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps) {
        ConfigurableRootQuery<?, E> query = createQuery(fetcher, null, sortedProps);
        return query.execute();
    }

    @NotNull
    @Override
    public <V extends View<E>> List<V> findAll(Class<V> viewType, TypedProp.Scalar<?, ?>... sortedProps) {
        DtoMetadata<E, V> metadata = DtoMetadata.of(viewType);
        ConfigurableRootQuery<?, V> query = createQuery(metadata.getFetcher(), metadata.getConverter(), sortedProps);
        return query.execute();
    }

    @NotNull
    @Override
    public Page<E> findPage(PageParam pageParam, @Nullable Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps) {
        ConfigurableRootQuery<?, E> query = createQuery(fetcher, null, sortedProps);
        return query.fetchPage(pageParam.getIndex(), pageParam.getSize());
    }

    @NotNull
    @Override
    public <V extends View<E>> Page<V> findPage(PageParam pageParam, Class<V> viewType, TypedProp.Scalar<?, ?>... sortedProps) {
        DtoMetadata<E, V> metadata = DtoMetadata.of(viewType);
        ConfigurableRootQuery<?, V> query = createQuery(metadata.getFetcher(), metadata.getConverter(), sortedProps);
        return query.fetchPage(pageParam.getIndex(), pageParam.getSize());
    }

    @NotNull
    @Override
    public Slice<E> findSlice(int limit, int offset, @Nullable Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps) {
        ConfigurableRootQuery<?, E> query = createQuery(fetcher, null, sortedProps);
        return query.fetchSlice(limit, offset);
    }

    @NotNull
    @Override
    public <V extends View<E>> Slice<V> findSlice(int limit, int offset, Class<V> viewType,
            TypedProp.Scalar<?, ?>... sortedProps) {
        DtoMetadata<E, V> metadata = DtoMetadata.of(viewType);
        ConfigurableRootQuery<?, V> query = createQuery(metadata.getFetcher(), metadata.getConverter(), sortedProps);
        return query.fetchSlice(limit, offset);
    }

    @Override
    public SimpleSaveResult<E> save(E entity, SaveMode mode, AssociatedSaveMode associatedMode) {
        return sql
                .getEntities()
                .saveCommand(entity)
                .setMode(mode)
                .setAssociatedModeAll(associatedMode)
                .execute();
    }

    @Override
    public BatchSaveResult<E> saveEntities(Iterable<E> entities, SaveMode mode, AssociatedSaveMode associatedMode) {
        return sql
                .getEntities()
                .saveEntitiesCommand(entities)
                .setMode(mode)
                .setAssociatedModeAll(associatedMode)
                .execute();
    }

    @Override
    public SimpleSaveResult<E> save(Input<E> input, SaveMode mode, AssociatedSaveMode associatedMode) {
        return sql
                .getEntities()
                .saveCommand(input.toEntity())
                .setMode(mode)
                .setAssociatedModeAll(associatedMode)
                .execute();
    }

    @Override
    public BatchSaveResult<E> saveInputs(Iterable<? extends Input<E>> inputs, SaveMode mode,
            AssociatedSaveMode associatedMode) {
        return sql
                .getEntities()
                .saveInputsCommand(inputs)
                .setMode(mode)
                .setAssociatedModeAll(associatedMode)
                .execute();
    }

    @Override
    public long deleteById(ID id, DeleteMode deleteMode) {
        return sql.deleteById(entityType, id, deleteMode).getAffectedRowCount(entityType);
    }

    @Override
    public long deleteByIds(Iterable<ID> ids, DeleteMode deleteMode) {
        return sql.deleteByIds(entityType, ids, deleteMode).getAffectedRowCount(entityType);
    }

    @SuppressWarnings("unchecked")
    private <X> ConfigurableRootQuery<?, X> createQuery(
            Fetcher<?> fetcher,
            @Nullable Function<?, X> converter,
            @Nullable TypedProp.Scalar<?, ?>[] sortedProps) {
        MutableRootQueryImpl<Table<?>> query = new MutableRootQueryImpl<>(
                (JSqlClientImplementor) sql,
                type,
                ExecutionPurpose.QUERY,
                FilterLevel.DEFAULT);
        TableImplementor<?> table = query.getTableImplementor();
        if (sortedProps != null) {
            for (TypedProp.Scalar<?, ?> sortedProp : sortedProps) {
                if (!sortedProp.unwrap().getDeclaringType().isAssignableFrom(type)) {
                    throw new IllegalArgumentException(
                            "The sorted field \"" +
                                    sortedProp +
                                    "\" does not belong to the type \"" +
                                    type +
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
        return query.select(
                fetcher != null ? new FetcherSelectionImpl<>(table, fetcher, converter) : (Selection<X>) table);
    }
}
