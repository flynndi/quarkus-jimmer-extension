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
import org.babyfish.jimmer.sql.kt.ast.mutation.KSaveCommandPartialDsl
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

    fun insert(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.insert(input, associatedMode, null, block).modifiedEntity

    fun insert(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.insert(entity, associatedMode, null, block).modifiedEntity

    fun insertIfAbsent(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND_IF_ABSENT,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.insert(input, associatedMode, null, block).modifiedEntity

    fun insertIfAbsent(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND_IF_ABSENT,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.insert(entity, associatedMode, null, block).modifiedEntity

    fun update(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.UPDATE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.update(input, associatedMode, null, block).modifiedEntity

    fun update(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.UPDATE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.update(entity, associatedMode, null, block).modifiedEntity

    fun merge(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.MERGE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.merge(input, null, block).modifiedEntity

    fun merge(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.MERGE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.merge(entity, null, block).modifiedEntity

    fun <S: E> save(entity: S): S =
        sql.save(entity, null).modifiedEntity

    fun <S: E> save(
        entity: S,
        block: (KSaveCommandDsl.() -> Unit)
    ): S =
        sql.save(entity, null, block).modifiedEntity

    fun save(input: Input<E>): E =
        sql.save(input, null).modifiedEntity

    fun save(
        input: Input<E>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): E =
        sql.save(input, null, block).modifiedEntity

    fun <S: E> save(
        entity: S,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): S =
        sql.save(entity, mode, associatedMode, null, block).modifiedEntity

    fun save(
        input: Input<E>,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.save(input, mode, associatedMode, null, block).modifiedEntity

    fun <S: E> save(
        entity: S,
        associatedMode: AssociatedSaveMode,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): S =
        sql.save(entity, associatedMode, null, block).modifiedEntity

    fun save(
        input: Input<E>,
        associatedMode: AssociatedSaveMode,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        sql.save(input, associatedMode, null, block).modifiedEntity

    fun <S : E> saveAll(entities: MutableIterable<S>): List<S> =
        saveEntities(entities, SaveMode.UPSERT).items.map { it.modifiedEntity }

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