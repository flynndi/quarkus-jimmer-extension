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
import io.quarkiverse.jimmer.runtime.repository.support.Utils;

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

    @SuppressWarnings("unchecked")
    List<E> findAll(TypedProp.Scalar<?, ?>... sortedProps);

    @SuppressWarnings("unchecked")
    List<E> findAll(Fetcher<E> fetcher, TypedProp.Scalar<?, ?>... sortedProps);

    @NotNull
    List<E> findAll(@NotNull Sort sort);

    List<E> findAll(Fetcher<E> fetcher, Sort sort);

    Page<E> findAll(int pageIndex, int pageSize);

    Page<E> findAll(int pageIndex, int pageSize, Fetcher<E> fetcher);

    @SuppressWarnings("unchecked")
    Page<E> findAll(int pageIndex, int pageSize, TypedProp.Scalar<?, ?>... sortedProps);

    @SuppressWarnings("unchecked")
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
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     *
     * <p>
     * For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.
     * </p>
     */
    default <S extends E> SimpleSaveResult<S> merge(@NotNull S entity) {
        return saveCommand(entity).setAssociatedModeAll(AssociatedSaveMode.MERGE).execute();
    }

    /**
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     *
     * <p>
     * For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.
     * </p>
     */
    default SimpleSaveResult<E> merge(@NotNull Input<E> input) {
        return saveCommand(input.toEntity()).setAssociatedModeAll(AssociatedSaveMode.MERGE).execute();
    }

    /**
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     *
     * <p>
     * For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.
     * </p>
     */
    default <S extends E> SimpleSaveResult<S> merge(@NotNull S entity, SaveMode mode) {
        return saveCommand(entity).setAssociatedModeAll(AssociatedSaveMode.MERGE).setMode(mode).execute();
    }

    /**
     * <p>
     * Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!
     * </p>
     *
     * <p>
     * For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.
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
    SimpleEntitySaveCommand<E> saveCommand(@NotNull Input<E> input);

    @NotNull
    <S extends E> SimpleEntitySaveCommand<S> saveCommand(@NotNull S entity);

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
    <S extends E> BatchEntitySaveCommand<S> saveEntitiesCommand(@NotNull Iterable<S> entities);

    @NotNull
    default <S extends E> BatchEntitySaveCommand<S> saveInputsCommand(@NotNull Iterable<Input<S>> inputs) {
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
