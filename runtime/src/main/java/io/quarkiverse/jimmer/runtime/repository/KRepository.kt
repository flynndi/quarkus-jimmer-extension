package io.quarkiverse.jimmer.runtime.repository

import io.quarkiverse.jimmer.runtime.repository.common.Sort
import io.quarkiverse.jimmer.runtime.repository.support.Pagination
import org.babyfish.jimmer.Input
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.View
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.mutation.KBatchSaveResult
import org.babyfish.jimmer.sql.kt.ast.mutation.KSaveCommandDsl
import org.babyfish.jimmer.sql.kt.ast.mutation.KSimpleSaveResult
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import java.util.*
import kotlin.reflect.KClass

interface KRepository<E: Any, ID: Any> {

    val sql: KSqlClient

    val type: ImmutableType

    val entityType: KClass<E>

    fun findNullable(id: ID, fetcher: Fetcher<E>? = null): E?

    fun findById(id: ID): Optional<E> =
        Optional.ofNullable(findNullable(id))

    fun findById(id: ID, fetcher: Fetcher<E>): Optional<E> =
        Optional.ofNullable(findNullable(id, fetcher))

    fun findByIds(ids: Iterable<ID>, fetcher: Fetcher<E>? = null): List<E>

    fun findAllById(ids: Iterable<ID>): List<E> =
        findByIds(ids)

    fun findMapByIds(ids: Iterable<ID>, fetcher: Fetcher<E>? = null): Map<ID, E>

    fun findAll(): List<E> =
        findAll(null)

    fun findAll(fetcher: Fetcher<E>? = null): List<E>

    fun findAll(fetcher: Fetcher<E>? = null, block: (SortDsl<E>.() -> Unit)): List<E>

    fun findAll(sort: Sort): List<E> =
        findAll(null, sort)

    fun findAll(fetcher: Fetcher<E>? = null, sort: Sort): List<E>

    fun findAll(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<E>? = null,
        block: (SortDsl<E>.() -> Unit)? = null
    ): Page<E>

    fun findAll(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<E>? = null,
        sort: Sort
    ): Page<E>

    fun findAll(pagination: Pagination): Page<E>

    fun findAll(pagination: Pagination, fetcher: Fetcher<E>? = null): Page<E>

    fun existsById(id: ID): Boolean =
        findNullable(id) != null

    fun count(): Long

    fun insert(input: Input<E>): E =
        save(input.toEntity(), SaveMode.INSERT_ONLY).modifiedEntity

    fun insert(entity: E): E =
        save(entity, SaveMode.INSERT_ONLY).modifiedEntity

    fun update(input: Input<E>): E =
        save(input.toEntity(), SaveMode.UPDATE_ONLY).modifiedEntity

    fun update(entity: E): E =
        save(entity, SaveMode.UPDATE_ONLY).modifiedEntity

    fun save(input: Input<E>): E =
        save(input.toEntity(), SaveMode.UPSERT).modifiedEntity

    fun <S: E> save(entity: S): S =
        save(entity, SaveMode.UPSERT).modifiedEntity

    fun save(input: Input<E>, mode: SaveMode): KSimpleSaveResult<E> =
        save(input.toEntity(), mode)

    fun <S: E> save(entity: S, mode: SaveMode): KSimpleSaveResult<S> =
        save(entity) {
            setMode(mode)
        }

    fun <S: E> save(entity: S, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S>

    fun <S: E> save(input: Input<S>, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S>

    /**
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     *
     * <p>For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.</p>
     */
    fun <S: E> merge(entity: S, mode: SaveMode = SaveMode.UPSERT): KSimpleSaveResult<S> =
        save(entity) {
            setAssociatedModeAll(AssociatedSaveMode.MERGE)
            setMode(mode)
        }

    /**
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     *
     * <p>For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.</p>
     */
    fun <S: E> merge(input: Input<S>, mode: SaveMode = SaveMode.UPSERT): KSimpleSaveResult<S> =
        save(input.toEntity()) {
            setAssociatedModeAll(AssociatedSaveMode.MERGE)
            setMode(mode)
        }

    /**
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     *
     * <p>For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.</p>
     */
    fun <S: E> merge(entity: S, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> =
        save(entity) {
            block()
            setAssociatedModeAll(AssociatedSaveMode.MERGE)
        }

    /**
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     *
     * <p>For associated objects, only insert or update operations are executed.
     * The parent object never dissociates the child objects.</p>
     */
    fun <S: E> merge(input: Input<S>, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> =
        save(input.toEntity()) {
            block()
            setAssociatedModeAll(AssociatedSaveMode.MERGE)
        }

    /**
     * For associated objects, only insert operations are executed.
     */
    fun <S: E> append(entity: S, mode: SaveMode = SaveMode.UPSERT): KSimpleSaveResult<S> =
        save(entity) {
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
            setMode(mode)
        }

    /**
     * For associated objects, only insert operations are executed.
     */
    fun <S: E> append(input: Input<S>, mode: SaveMode = SaveMode.UPSERT): KSimpleSaveResult<S> =
        save(input.toEntity()) {
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
            setMode(mode)
        }

    /**
     * For associated objects, only insert operations are executed.
     */
    fun <S: E> append(entity: S, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> =
        save(entity) {
            block()
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
        }

    /**
     * For associated objects, only insert operations are executed.
     */
    fun <S: E> append(input: Input<S>, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> =
        save(input.toEntity()) {
            block()
            setAssociatedModeAll(AssociatedSaveMode.APPEND)
        }

    @Deprecated(
        "Replaced by \"saveEntities\", will be removed in 1.0",
        replaceWith = ReplaceWith("")
    )
    fun <S : E> saveAll(entities: MutableIterable<S>): List<S> =
        saveEntities(entities, SaveMode.UPSERT).simpleResults.map { it.modifiedEntity }

    fun <S : E> saveEntities(entities: Iterable<S>): KBatchSaveResult<S> =
        saveEntities(entities, SaveMode.UPSERT)

    fun <S : E> saveEntities(entities: Iterable<S>, mode: SaveMode): KBatchSaveResult<S> =
        saveEntities(entities) {
            setMode(mode)
        }

    fun <S : E> saveEntities(entities: Iterable<S>, block: KSaveCommandDsl.() -> Unit): KBatchSaveResult<S>

    fun <S : E> saveInputs(inputs: Iterable<Input<S>>): KBatchSaveResult<S> =
        saveInputs(inputs, SaveMode.UPSERT)

    fun <S : E> saveInputs(inputs: Iterable<Input<S>>, mode: SaveMode): KBatchSaveResult<S> =
        saveInputs(inputs) {
            setMode(mode)
        }

    fun <S : E> saveInputs(inputs: Iterable<Input<S>>, block: KSaveCommandDsl.() -> Unit): KBatchSaveResult<S>

    fun delete(entity: E) {
        delete(entity, DeleteMode.AUTO)
    }

    fun delete(entity: E, mode: DeleteMode): Int

    fun deleteById(id: ID) {
        deleteById(id, DeleteMode.AUTO)
    }

    fun deleteById(id: ID, mode: DeleteMode): Int

    fun deleteByIds(ids: Iterable<ID>) {
        deleteByIds(ids, DeleteMode.AUTO)
    }

    fun deleteAllById(ids: Iterable<ID>) {
        deleteByIds(ids, DeleteMode.AUTO)
    }

    fun deleteByIds(ids: Iterable<ID>, mode: DeleteMode): Int

    fun deleteAll(entities: Iterable<E>) {
        deleteAll(entities, DeleteMode.AUTO)
    }

    fun deleteAll(entities: Iterable<E>, mode: DeleteMode): Int

    fun deleteAll()

    fun <V: View<E>> viewer(viewType: KClass<V>): Viewer<E, ID, V>

    interface Viewer<E: Any, ID, V: View<E>> {

        fun findNullable(id: ID): V?

        fun findByIds(ids: Iterable<ID>?): List<V>

        fun findMapByIds(ids: Iterable<ID>?): Map<ID, V>

        fun findAll(): List<V>

        fun findAll(block: (SortDsl<E>.() -> Unit)): List<V>

        fun findAll(sort: Sort): List<V>

        fun findAll(pagination: Pagination): Page<V>

        fun findAll(pageIndex: Int, pageSize: Int): Page<V>

        fun findAll(pageIndex: Int, pageSize: Int, block: (SortDsl<E>.() -> Unit)): Page<V>

        fun findAll(pageIndex: Int, pageSize: Int, sort: Sort): Page<V>
    }
}