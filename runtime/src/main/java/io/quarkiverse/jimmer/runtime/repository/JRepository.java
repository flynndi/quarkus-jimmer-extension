package io.quarkiverse.jimmer.runtime.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.babyfish.jimmer.Input;
import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.View;
import org.babyfish.jimmer.impl.util.CollectionUtils;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.TypedProp;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.*;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.jetbrains.annotations.NotNull;

import io.quarkiverse.jimmer.runtime.repository.common.Sort;
import io.quarkiverse.jimmer.runtime.repository.support.Pagination;

public interface JRepository<E, ID> {

    JSqlClient sql();

    ImmutableType type();

    Class<E> entityType();

    E findNullable(ID id);

    E findNullable(ID id, Fetcher<E> fetcher);

    @NotNull
    default Optional<E> findById(@NotNull ID id) {
        return Optional.ofNullable(findNullable(id));
    }

    @NotNull
    default Optional<E> findById(ID id, Fetcher<E> fetcher) {
        return Optional.ofNullable(findNullable(id, fetcher));
    }

    List<E> findByIds(Iterable<ID> ids);

    @NotNull
    default List<E> findAllById(@NotNull Iterable<ID> ids) {
        return findByIds(ids);
    }

    List<E> findByIds(Iterable<ID> ids, Fetcher<E> fetcher);

    Map<ID, E> findMapByIds(Iterable<ID> ids);

    Map<ID, E> findMapByIds(Iterable<ID> ids, Fetcher<E> fetcher);

    @NotNull
    List<E> findAll();

    List<E> findAll(TypedProp.Scalar<?, ?>... sortedProps);

    List<E> findAll(Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    List<E> findAll(@NotNull Sort sort);

    List<E> findAll(Fetcher<E> fetcher, Sort sort);

    Page<E> findAll(int pageIndex, int pageSize);

    Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher);

    Page<E> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps);

    Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps);

    Page<E> findAll(int pageIndex, int pageSize, Sort sort);

    Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher, Sort sort);

    @NotNull
    Page<E> findAll(@NotNull Pagination pagination);

    Page<E> findAll(Pagination pagination, Fetcher<E> fetcher);

    default boolean existsById(@NotNull ID id) {
        return findNullable(id) != null;
    }

    long count();

    @NotNull
    default <S extends E> S save(@NotNull S entity) {
        return saveCommand(entity).execute().getModifiedEntity();
    }

    @NotNull
    default <S extends E> SimpleSaveResult<S> save(@NotNull S entity, SaveMode mode) {
        return saveCommand(entity).setMode(mode).execute();
    }

    @NotNull
    default <S extends E> SimpleSaveResult<S> save(@NotNull S entity, AssociatedSaveMode associatedMode) {
        return saveCommand(entity).setAssociatedModeAll(associatedMode).execute();
    }

    @NotNull
    default <S extends E> SimpleSaveResult<S> save(@NotNull S entity, SaveMode mode, AssociatedSaveMode associatedMode) {
        return saveCommand(entity).setMode(mode).setAssociatedModeAll(associatedMode).execute();
    }

    @NotNull
    default E save(@NotNull Input<E> input) {
        return saveCommand(input.toEntity()).execute().getModifiedEntity();
    }

    @NotNull
    default SimpleSaveResult<E> save(@NotNull Input<E> input, SaveMode mode) {
        return saveCommand(input.toEntity()).setMode(mode).execute();
    }

    @NotNull
    default SimpleSaveResult<E> save(@NotNull Input<E> input, AssociatedSaveMode associatedMode) {
        return saveCommand(input.toEntity()).setAssociatedModeAll(associatedMode).execute();
    }

    @NotNull
    default SimpleSaveResult<E> save(@NotNull Input<E> input, SaveMode mode, AssociatedSaveMode associatedMode) {
        return saveCommand(input.toEntity()).setMode(mode).setAssociatedModeAll(associatedMode).execute();
    }

    @NotNull
    default E insert(@NotNull E entity) {
        return save(entity, SaveMode.INSERT_ONLY, AssociatedSaveMode.APPEND).getModifiedEntity();
    }

    @NotNull
    default E insert(@NotNull E entity, AssociatedSaveMode associatedMode) {
        return save(entity, SaveMode.INSERT_ONLY, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E insert(@NotNull Input<E> input) {
        return save(input.toEntity(), SaveMode.INSERT_ONLY, AssociatedSaveMode.APPEND_IF_ABSENT).getModifiedEntity();
    }

    @NotNull
    default E insert(@NotNull Input<E> input, AssociatedSaveMode associatedMode) {
        return save(input.toEntity(), SaveMode.INSERT_ONLY, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E insertIfAbsent(@NotNull E entity) {
        return save(entity, SaveMode.INSERT_IF_ABSENT, AssociatedSaveMode.APPEND_IF_ABSENT).getModifiedEntity();
    }

    @NotNull
    default E insertIfAbsent(@NotNull E entity, AssociatedSaveMode associatedMode) {
        return save(entity, SaveMode.INSERT_IF_ABSENT, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E insertIfAbsent(@NotNull Input<E> input) {
        return save(input.toEntity(), SaveMode.INSERT_IF_ABSENT, AssociatedSaveMode.APPEND_IF_ABSENT).getModifiedEntity();
    }

    @NotNull
    default E insertIfAbsent(@NotNull Input<E> input, AssociatedSaveMode associatedMode) {
        return save(input.toEntity(), SaveMode.INSERT_IF_ABSENT, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E update(@NotNull E entity) {
        return save(entity, SaveMode.UPDATE_ONLY, AssociatedSaveMode.UPDATE).getModifiedEntity();
    }

    @NotNull
    default E update(@NotNull E entity, AssociatedSaveMode associatedMode) {
        return save(entity, SaveMode.UPDATE_ONLY, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E update(@NotNull Input<E> input) {
        return save(input.toEntity(), SaveMode.UPDATE_ONLY, AssociatedSaveMode.UPDATE).getModifiedEntity();
    }

    @NotNull
    default E update(@NotNull Input<E> input, AssociatedSaveMode associatedMode) {
        return save(input.toEntity(), SaveMode.UPDATE_ONLY, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E merge(@NotNull E entity) {
        return save(entity, SaveMode.UPSERT, AssociatedSaveMode.MERGE).getModifiedEntity();
    }

    @NotNull
    default E merge(@NotNull E entity, AssociatedSaveMode associatedMode) {
        return save(entity, SaveMode.UPSERT, associatedMode).getModifiedEntity();
    }

    @NotNull
    default E merge(@NotNull Input<E> input) {
        return save(input.toEntity(), SaveMode.UPSERT, AssociatedSaveMode.MERGE).getModifiedEntity();
    }

    @NotNull
    default E merge(@NotNull Input<E> input, AssociatedSaveMode associatedMode) {
        return save(input.toEntity(), SaveMode.UPSERT, associatedMode).getModifiedEntity();
    }

    @NotNull
    SimpleEntitySaveCommand<E> saveCommand(@NotNull Input<E> input);

    @NotNull
    <S extends E> SimpleEntitySaveCommand<S> saveCommand(@NotNull S entity);

    default <S extends E> Iterable<S> saveAll(@NotNull Iterable<S> entities) {
        return saveEntities(entities);
    }

    @NotNull
    default <S extends E> Iterable<S> saveEntities(@NotNull Iterable<S> entities) {
        return saveEntitiesCommand(entities)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveEntities(@NotNull Iterable<S> entities, SaveMode mode) {
        return saveEntitiesCommand(entities)
                .setMode(mode)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveEntities(@NotNull Iterable<S> entities, AssociatedSaveMode associatedMode) {
        return saveEntitiesCommand(entities)
                .setAssociatedModeAll(associatedMode)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveEntities(
            @NotNull Iterable<S> entities,
            SaveMode mode,
            AssociatedSaveMode associatedMode) {
        return saveEntitiesCommand(entities)
                .setMode(mode)
                .setAssociatedModeAll(associatedMode)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveInputs(@NotNull Iterable<? extends Input<S>> entities) {
        return saveInputsCommand(entities)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveInputs(@NotNull Iterable<? extends Input<S>> entities, SaveMode mode) {
        return saveInputsCommand(entities)
                .setMode(mode)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveInputs(@NotNull Iterable<? extends Input<S>> entities,
            AssociatedSaveMode associatedMode) {
        return saveInputsCommand(entities)
                .setAssociatedModeAll(associatedMode)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    default <S extends E> Iterable<S> saveInputs(
            @NotNull Iterable<? extends Input<S>> entities,
            SaveMode mode,
            AssociatedSaveMode associatedMode) {
        return saveInputsCommand(entities)
                .setMode(mode)
                .setAssociatedModeAll(associatedMode)
                .execute()
                .getItems()
                .stream()
                .map(BatchSaveResult.Item::getModifiedEntity)
                .collect(Collectors.toList());
    }

    @NotNull
    <S extends E> BatchEntitySaveCommand<S> saveEntitiesCommand(@NotNull Iterable<S> entities);

    @NotNull
    default <S extends E> BatchEntitySaveCommand<S> saveInputsCommand(@NotNull Iterable<? extends Input<S>> inputs) {
        return saveEntitiesCommand(CollectionUtils.map(inputs, Input::toEntity));
    }

    default void delete(@NotNull E entity) {
        delete(entity, DeleteMode.AUTO);
    }

    int delete(@NotNull E entity, DeleteMode mode);

    default void deleteAll(@NotNull Iterable<? extends E> entities) {
        deleteAll(entities, DeleteMode.AUTO);
    }

    int deleteAll(@NotNull Iterable<? extends E> entities, DeleteMode mode);

    default void deleteById(@NotNull ID id) {
        deleteById(id, DeleteMode.AUTO);
    }

    int deleteById(@NotNull ID id, DeleteMode mode);

    default void deleteByIds(Iterable<? extends ID> ids) {
        deleteByIds(ids, DeleteMode.AUTO);
    }

    default void deleteAllById(@NotNull Iterable<? extends ID> ids) {
        deleteByIds(ids, DeleteMode.AUTO);
    }

    int deleteByIds(Iterable<? extends ID> ids, DeleteMode mode);

    void deleteAll();

    <V extends View<E>> Viewer<E, ID, V> viewer(Class<V> viewType);

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