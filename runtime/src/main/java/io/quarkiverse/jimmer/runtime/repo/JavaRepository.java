package io.quarkiverse.jimmer.runtime.repo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.Slice;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.sql.ast.mutation.*;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JavaRepository<E, ID> {

    @Nullable
    default E findById(ID id) {
        return findById(id, (Fetcher<E>) null);
    }

    @Nullable
    E findById(ID id, @Nullable Fetcher<E> fetcher);

    @Nullable
    <V extends View<E>> V findById(ID id, Class<V> viewType);

    @NotNull
    default List<E> findByIds(Collection<ID> ids) {
        return findByIds(ids, (Fetcher<E>) null);
    }

    @NotNull
    List<E> findByIds(Collection<ID> ids, @Nullable Fetcher<E> fetcher);

    @NotNull
    <V extends View<E>> List<V> findByIds(Collection<ID> ids, Class<V> viewType);

    @NotNull
    default Map<ID, E> findMapByIds(Collection<ID> ids) {
        return findMapByIds(ids, (Fetcher<E>) null);
    }

    @NotNull
    Map<ID, E> findMapByIds(Collection<ID> ids, Fetcher<E> fetcher);

    @NotNull
    <V extends View<E>> Map<ID, V> findMapByIds(Collection<ID> ids, Class<V> viewType);

    @NotNull
    default List<E> findAll(TypedProp.Scalar<?, ?>... sortedProps) {
        return findAll((Fetcher<E>) null, sortedProps);
    }

    @NotNull
    List<E> findAll(@Nullable Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    <V extends View<E>> List<V> findAll(Class<V> viewType, TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    default Page<E> findPage(PageParam pageParam, TypedProp.Scalar<?, ?>... sortedProps) {
        return findPage(pageParam, (Fetcher<E>) null, sortedProps);
    }

    @NotNull
    Page<E> findPage(PageParam pageParam, @Nullable Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    <V extends View<E>> Page<V> findPage(
            PageParam pageParam,
            Class<V> viewType,
            TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    default Slice<E> findSlice(int limit, int offset, TypedProp.Scalar<?, ?>... sortedProps) {
        return findSlice(limit, offset, (Fetcher<E>) null, sortedProps);
    }

    @NotNull
    Slice<E> findSlice(int limit, int offset, @Nullable Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    <V extends View<E>> Slice<V> findSlice(
            int limit,
            int offset,
            Class<V> viewType,
            TypedProp.Scalar<?, ?>... sortedProps);

    default SimpleSaveResult<E> save(E entity) {
        return save(entity, SaveMode.UPSERT, AssociatedSaveMode.REPLACE);
    }

    default SimpleSaveResult<E> save(E entity, SaveMode mode) {
        return save(entity, mode, AssociatedSaveMode.REPLACE);
    }

    default SimpleSaveResult<E> save(E entity, AssociatedSaveMode associatedMode) {
        return save(entity, SaveMode.UPSERT, associatedMode);
    }

    SimpleSaveResult<E> save(E entity, SaveMode mode, AssociatedSaveMode associatedMode);

    default SimpleSaveResult<E> insert(E entity) {
        return save(entity, SaveMode.INSERT_ONLY, AssociatedSaveMode.APPEND);
    }

    default SimpleSaveResult<E> insert(E entity, AssociatedSaveMode associatedSaveMode) {
        return save(entity, SaveMode.INSERT_ONLY, associatedSaveMode);
    }

    default SimpleSaveResult<E> insert(Input<E> input) {
        return save(input, SaveMode.INSERT_ONLY, AssociatedSaveMode.APPEND);
    }

    default SimpleSaveResult<E> insert(Input<E> input, AssociatedSaveMode associatedSaveMode) {
        return save(input, SaveMode.INSERT_ONLY, associatedSaveMode);
    }

    default SimpleSaveResult<E> insertIfAbsent(E entity) {
        return save(entity, SaveMode.INSERT_IF_ABSENT, AssociatedSaveMode.APPEND_IF_ABSENT);
    }

    default SimpleSaveResult<E> insertIfAbsent(E entity, AssociatedSaveMode associatedSaveMode) {
        return save(entity, SaveMode.INSERT_IF_ABSENT, associatedSaveMode);
    }

    default SimpleSaveResult<E> insertIfAbsent(Input<E> input) {
        return save(input, SaveMode.INSERT_IF_ABSENT, AssociatedSaveMode.APPEND_IF_ABSENT);
    }

    default SimpleSaveResult<E> insertIfAbsent(Input<E> input, AssociatedSaveMode associatedSaveMode) {
        return save(input, SaveMode.INSERT_IF_ABSENT, associatedSaveMode);
    }

    default SimpleSaveResult<E> update(E entity) {
        return save(entity, SaveMode.UPDATE_ONLY, AssociatedSaveMode.UPDATE);
    }

    default SimpleSaveResult<E> update(E entity, AssociatedSaveMode associatedSaveMode) {
        return save(entity, SaveMode.UPDATE_ONLY, associatedSaveMode);
    }

    default SimpleSaveResult<E> update(Input<E> input) {
        return save(input, SaveMode.UPDATE_ONLY, AssociatedSaveMode.UPDATE);
    }

    default SimpleSaveResult<E> update(Input<E> input, AssociatedSaveMode associatedSaveMode) {
        return save(input, SaveMode.UPDATE_ONLY, associatedSaveMode);
    }

    default SimpleSaveResult<E> merge(E entity) {
        return save(entity, SaveMode.UPSERT, AssociatedSaveMode.MERGE);
    }

    default SimpleSaveResult<E> merge(E entity, AssociatedSaveMode associatedSaveMode) {
        return save(entity, SaveMode.UPSERT, associatedSaveMode);
    }

    default SimpleSaveResult<E> merge(Input<E> input) {
        return save(input, SaveMode.UPSERT, AssociatedSaveMode.MERGE);
    }

    default SimpleSaveResult<E> merge(Input<E> input, AssociatedSaveMode associatedSaveMode) {
        return save(input, SaveMode.UPSERT, associatedSaveMode);
    }

    default BatchSaveResult<E> saveEntities(Collection<E> entities) {
        return saveEntities(entities, SaveMode.UPSERT, AssociatedSaveMode.REPLACE);
    }

    default BatchSaveResult<E> saveEntities(Collection<E> entities, SaveMode mode) {
        return saveEntities(entities, mode, AssociatedSaveMode.REPLACE);
    }

    default BatchSaveResult<E> saveEntities(Collection<E> entities, AssociatedSaveMode associatedMode) {
        return saveEntities(entities, SaveMode.UPSERT, associatedMode);
    }

    BatchSaveResult<E> saveEntities(
            Collection<E> entities,
            SaveMode rootSaveMode,
            AssociatedSaveMode associatedSaveMode);

    default SimpleSaveResult<E> save(Input<E> input) {
        return save(input, SaveMode.UPSERT, AssociatedSaveMode.REPLACE);
    }

    default SimpleSaveResult<E> save(Input<E> input, SaveMode mode) {
        return save(input, mode, AssociatedSaveMode.REPLACE);
    }

    default SimpleSaveResult<E> save(Input<E> input, AssociatedSaveMode associatedMode) {
        return save(input, SaveMode.UPSERT, associatedMode);
    }

    SimpleSaveResult<E> save(
            Input<E> input,
            SaveMode rootSaveMode,
            AssociatedSaveMode associatedSaveMode);

    default BatchSaveResult<E> saveInputs(Collection<Input<E>> inputs) {
        return saveInputs(inputs, SaveMode.UPSERT, AssociatedSaveMode.REPLACE);
    }

    default BatchSaveResult<E> saveInputs(Collection<Input<E>> inputs, SaveMode mode) {
        return saveInputs(inputs, mode, AssociatedSaveMode.REPLACE);
    }

    default BatchSaveResult<E> saveInputs(Collection<Input<E>> inputs, AssociatedSaveMode associatedMode) {
        return saveInputs(inputs, SaveMode.UPSERT, associatedMode);
    }

    BatchSaveResult<E> saveInputs(
            Collection<Input<E>> inputs,
            SaveMode rootSaveMode,
            AssociatedSaveMode associatedSaveMode);

    default long deleteById(ID id) {
        return deleteById(id, DeleteMode.AUTO);
    }

    long deleteById(ID id, DeleteMode deleteMode);

    default long deleteByIds(Collection<ID> ids) {
        return deleteByIds(ids, DeleteMode.AUTO);
    }

    long deleteByIds(Collection<ID> ids, DeleteMode deleteMode);
}
