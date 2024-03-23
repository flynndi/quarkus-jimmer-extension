package io.quarkiverse.jimmer.runtime.repository

import io.quarkiverse.jimmer.runtime.Jimmer
import io.quarkiverse.jimmer.runtime.repository.common.Sort
import io.quarkiverse.jimmer.runtime.repository.support.JpaOperationsData
import io.quarkiverse.jimmer.runtime.repository.support.Pagination
import io.quarkiverse.jimmer.runtime.repository.support.Utils
import io.quarkus.agroal.DataSource
import org.babyfish.jimmer.ImmutableObjects
import org.babyfish.jimmer.Input
import org.babyfish.jimmer.Page
import org.babyfish.jimmer.View
import org.babyfish.jimmer.meta.ImmutableType
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode
import org.babyfish.jimmer.sql.ast.mutation.SaveMode
import org.babyfish.jimmer.sql.fetcher.Fetcher
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.ast.mutation.KBatchSaveResult
import org.babyfish.jimmer.sql.kt.ast.mutation.KSaveCommandDsl
import org.babyfish.jimmer.sql.kt.ast.mutation.KSimpleSaveResult
import org.babyfish.jimmer.sql.kt.ast.query.SortDsl
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor
import java.util.*
import kotlin.reflect.KClass

interface KRepository<E: Any, ID: Any> {

    fun sqlClient(): JSqlClientImplementor {
        return Utils.validateSqlClient(sql().javaClient)
    }

    fun sql(): KSqlClient {
        if (null != this.javaClass.getAnnotation(DataSource::class.java)) {
            return Jimmer.getKSqlClient(this.javaClass.getAnnotation(DataSource::class.java).value)
        }
        return Jimmer.getDefaultKSqlClient()
    }

    fun type(): ImmutableType {
        return JpaOperationsData.getImmutableType(this.javaClass)
    }

    @Suppress("UNCHECKED_CAST")
    fun entityType(): KClass<E> {
        return JpaOperationsData.getEntityKClass(this.javaClass) as KClass<E>
    }

    /*
     * For consumer
     */
    fun findNullable(id: ID, fetcher: Fetcher<E>? = null): E? {
        return if (fetcher !== null) {
            sql().entities.findById(fetcher, id)
        } else {
            sql().entities.findById(entityType(), id)
        }
    }

    fun findById(id: ID): Optional<E> =
        Optional.ofNullable(findNullable(id))

    fun findById(id: ID, fetcher: Fetcher<E>): Optional<E> =
        Optional.ofNullable(findNullable(id, fetcher))

    fun findByIds(ids: Iterable<ID>, fetcher: Fetcher<E>? = null): List<E> {
        if (fetcher !== null) {
            return sql().entities.findByIds(fetcher, Utils.toCollection(ids))
        } else {
            return sql().entities.findByIds(entityType(), Utils.toCollection(ids))
        }
    }

    fun findAllById(ids: Iterable<ID>): List<E> =
        findByIds(ids)

    fun findMapByIds(ids: Iterable<ID>, fetcher: Fetcher<E>? = null): Map<ID, E> {
        if (fetcher !== null) {
            return sql().entities.findMapByIds(fetcher, Utils.toCollection(ids))
        } else {
            return sql().entities.findMapByIds(entityType(), Utils.toCollection(ids))
        }
    }

    fun findAll(): List<E> =
        findAll(null)

    fun findAll(fetcher: Fetcher<E>? = null): List<E> {
        return if (fetcher !== null) {
            sql().entities.findAll(fetcher)
        } else {
            sql().entities.findAll(entityType())
        }
    }

    fun findAll(fetcher: Fetcher<E>? = null, block: (SortDsl<E>.() -> Unit)): List<E> {
        if (fetcher !== null) {
            return sql().entities.findAll(fetcher, block)
        } else {
            return sql().entities.findAll(entityType(), block)
        }
    }

    fun findAll(sort: Sort): List<E> =
        findAll(null, sort)

    fun findAll(fetcher: Fetcher<E>? = null, sort: Sort): List<E> {
        return sql().createQuery(entityType()) {
            orderBy(sort)
            select(table.fetch(fetcher))
        }.execute()
    }

    fun findAll(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<E>? = null,
        block: (SortDsl<E>.() -> Unit)? = null
    ): Page<E> {
        return sql().createQuery(entityType()) {
            orderBy(block)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    fun findAll(
        pageIndex: Int,
        pageSize: Int,
        fetcher: Fetcher<E>? = null,
        sort: Sort
    ): Page<E> {
        return sql().createQuery(entityType()) {
            orderBy(sort)
            select(table.fetch(fetcher))
        }.fetchPage(pageIndex, pageSize)
    }

    fun findAll(pagination: Pagination): Page<E> {
        return findAll(pagination, null)
    }

    fun findAll(pagination: Pagination, fetcher: Fetcher<E>? = null): Page<E> {
        return sql().createQuery(entityType()) {
            select(table.fetch(fetcher))
        }.fetchPage(pagination.index, pagination.size)
    }

    fun existsById(id: ID): Boolean =
        findNullable(id) != null

    fun count(): Long {
        return sql().createQuery(entityType()) {
            select(org.babyfish.jimmer.sql.kt.ast.expression.count(table))
        }.fetchOne()
    }

    fun <S: E> save(entity: S, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> {
        return sql().entities.save(entity, block = block)
    }

    fun <S: E> save(input: Input<S>, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> {
        return sql().entities.save(input, block = block)
    }

    fun <S : E> saveEntities(entities: Iterable<S>, block: KSaveCommandDsl.() -> Unit): KBatchSaveResult<S> {
        return sql().entities.saveEntities(Utils.toCollection(entities), block = block)
    }

    fun <S : E> saveInputs(inputs: Iterable<Input<S>>, block: KSaveCommandDsl.() -> Unit): KBatchSaveResult<S> {
        return sql().entities.saveEntities(inputs.map { it.toEntity() }, block = block)
    }

    fun delete(entity: E, mode: DeleteMode): Int {
        return sql().entities.delete(
            entityType(),
            ImmutableObjects.get(entity, type().idProp)
        ) {
            setMode(mode)
        }.affectedRowCount(entityType())
    }

    fun deleteById(id: ID, mode: DeleteMode): Int {
        return sql().entities.delete(entityType(), id) {
            setMode(mode)
        }.affectedRowCount(entityType())
    }

    fun deleteByIds(ids: Iterable<ID>, mode: DeleteMode): Int {
        return sql().entities.deleteAll(entityType(), Utils.toCollection(ids)) {
            setMode(mode)
        }.affectedRowCount(entityType())
    }

    fun deleteAll(entities: Iterable<E>, mode: DeleteMode): Int {
        return sql()
            .entities
            .deleteAll(
                entityType(),
                entities.map {
                    ImmutableObjects.get(it, type().idProp)
                }
            ) {
                setMode(mode)
            }.affectedRowCount(entityType())
    }

    fun deleteAll() {
        sql().createDelete(entityType()) {
        }.execute()
    }

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

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     */
    fun <S: E> merge(entity: S, mode: SaveMode = SaveMode.UPSERT): KSimpleSaveResult<S> =
        save(entity) {
            setMergeMode()
            setMode(mode)
        }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     */
    fun <S: E> merge(input: Input<S>, mode: SaveMode = SaveMode.UPSERT): KSimpleSaveResult<S> =
        save(input.toEntity()) {
            setMergeMode()
            setMode(mode)
        }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     */
    fun <S: E> merge(entity: S, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> =
        save(entity) {
            block()
            setMergeMode()
        }

    /**
     * Unlike save, merge is significantly different,
     * only the insert and update operations will be executed,
     * dissociation operations will never be executed.
     *
     * <p>Note: The 'merge' of 'Jimmer' and the 'merge' of 'JPA' are completely different concepts!</p>
     */
    fun <S: E> merge(input: Input<S>, block: KSaveCommandDsl.() -> Unit): KSimpleSaveResult<S> =
        save(input.toEntity()) {
            block()
            setMergeMode()
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

    fun <S : E> saveInputs(inputs: Iterable<Input<S>>): KBatchSaveResult<S> =
        saveInputs(inputs, SaveMode.UPSERT)

    fun <S : E> saveInputs(inputs: Iterable<Input<S>>, mode: SaveMode): KBatchSaveResult<S> =
        saveInputs(inputs) {
            setMode(mode)
        }

    fun delete(entity: E) {
        delete(entity, DeleteMode.AUTO)
    }

    fun deleteById(id: ID) {
        deleteById(id, DeleteMode.AUTO)
    }

    fun deleteByIds(ids: Iterable<ID>) {
        deleteByIds(ids, DeleteMode.AUTO)
    }

    fun deleteAllById(ids: Iterable<ID>) {
        deleteByIds(ids, DeleteMode.AUTO)
    }

    fun deleteAll(entities: Iterable<E>) {
        deleteAll(entities, DeleteMode.AUTO)
    }

    fun <V: View<E>> viewer(viewType: KClass<V>): Viewer<E, ID, V> {
        return object : Viewer<E, ID, V> {

            override fun findNullable(id: ID): V? {
                return sql().entities.findById(viewType, id)
            }

            override fun findByIds(ids: Iterable<ID>?): List<V> {
                return sql().entities.findByIds(viewType, Utils.toCollection(ids))
            }

            override fun findMapByIds(ids: Iterable<ID>?): Map<ID, V> {
                return sql().entities.findMapByIds(viewType, Utils.toCollection(ids))
            }

            override fun findAll(): List<V> {
                return sql().entities.findAll(viewType)
            }

            override fun findAll(block: SortDsl<E>.() -> Unit): List<V> {
                return sql().createQuery(entityType()) {
                    orderBy(block)
                    select(table.fetch(viewType))
                }.execute()
            }

            override fun findAll(sort: Sort): List<V> {
                return sql().createQuery(entityType()) {
                    orderBy(sort)
                    select(table.fetch(viewType))
                }.execute()
            }

            override fun findAll(pagination: Pagination): Page<V> {
                return sql().createQuery(entityType()) {
                    select(table.fetch(viewType))
                }.fetchPage(pagination.index, pagination.size)
            }

            override fun findAll(pageIndex: Int, pageSize: Int): Page<V> {
                return sql().createQuery(entityType()) {
                    select(table.fetch(viewType))
                }.fetchPage(pageIndex, pageSize)
            }

            override fun findAll(pageIndex: Int, pageSize: Int, block: SortDsl<E>.() -> Unit): Page<V> {
                return sql().createQuery(entityType()) {
                    orderBy(block)
                    select(table.fetch(viewType))
                }.fetchPage(pageIndex, pageSize)
            }

            override fun findAll(pageIndex: Int, pageSize: Int, sort: Sort): Page<V> {
                return sql().createQuery(entityType()) {
                    orderBy(sort)
                    select(table.fetch(viewType))
                }.fetchPage(pageIndex, pageSize)
            }
        }
    }


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