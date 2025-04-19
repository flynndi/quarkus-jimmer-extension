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
import org.babyfish.jimmer.sql.kt.ast.mutation.KBatchEntitySaveCommand
import org.babyfish.jimmer.sql.kt.ast.mutation.KBatchSaveResult
import org.babyfish.jimmer.sql.kt.ast.mutation.KSaveCommandDsl
import org.babyfish.jimmer.sql.kt.ast.mutation.KSaveCommandPartialDsl
import org.babyfish.jimmer.sql.kt.ast.mutation.KSimpleEntitySaveCommand
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

    fun saveCommand(
        entity: E,
        block: (KSaveCommandDsl.() -> Unit) ?= null
    ): KSimpleEntitySaveCommand<E> =
        sql.saveCommand(entity, block)

    fun saveCommand(
        entity: E,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit) ?= null
    ): KSimpleEntitySaveCommand<E> = saveCommand(entity) {
        setMode(mode)
        setAssociatedModeAll(associatedMode)
        block?.invoke(this)
    }

    fun saveCommand(
        input: Input<E>,
        block: (KSaveCommandDsl.() -> Unit) ?= null
    ): KSimpleEntitySaveCommand<E> =
        saveCommand(input.toEntity(), block)

    fun saveCommand(
        input: Input<E>,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit) ?= null
    ): KSimpleEntitySaveCommand<E> =
        saveCommand(input.toEntity()) {
            setMode(mode)
            setAssociatedModeAll(associatedMode)
            block?.invoke(this)
        }

    fun saveEntitiesCommand(
        entities: Iterable<E>,
        block: (KSaveCommandDsl.() -> Unit) ?= null
    ): KBatchEntitySaveCommand<E> =
        sql.saveEntitiesCommand(entities, block)

    fun saveEntitiesCommand(
        entities: Iterable<E>,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit) ?= null
    ): KBatchEntitySaveCommand<E> = saveEntitiesCommand(entities) {
        setMode(mode)
        setAssociatedModeAll(associatedMode)
        block?.invoke(this)
    }

    fun saveInputsCommand(
        input: Iterable<Input<E>>,
        block: (KSaveCommandDsl.() -> Unit) ?= null
    ): KBatchEntitySaveCommand<E> =
        saveEntitiesCommand(input.map { it.toEntity() }, block)

    fun saveInputsCommand(
        inputs: Iterable<Input<E>>,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit) ?= null
    ): KBatchEntitySaveCommand<E> =
        saveEntitiesCommand(inputs.map { it.toEntity() }) {
            setMode(mode)
            setAssociatedModeAll(associatedMode)
            block?.invoke(this)
        }

    @Suppress("UNCHECKED_CAST")
    fun <S:E> save(
        entity: S,
    ): S =
        saveCommand(entity)
            .execute()
            .modifiedEntity as S

    @Suppress("UNCHECKED_CAST")
    fun <S:E> saveAll(
        entity: Iterable<S>,
    ): List<S> =
        saveEntitiesCommand(entity as List<E>)
            .execute()
            .items
            .map { it.modifiedEntity as S }

    fun save(
        entity: E ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        saveCommand(entity, mode, associatedMode, block)
            .execute()
            .modifiedEntity

    fun saveEntities(
        entities: Iterable<E> ,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): List<E> =
        saveEntitiesCommand(entities, block)
            .execute()
            .items.map { it.modifiedEntity }

    fun saveEntities(
        entities: Iterable<E> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): List<E> =
        saveEntitiesCommand(entities, mode, associatedMode, block)
            .execute()
            .items.map { it.modifiedEntity }

    fun save(
        input: Input<E>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): E =
        saveCommand(input, block)
            .execute()
            .modifiedEntity

    fun save(
        input: Input<E> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        saveCommand(input, mode, associatedMode, block)
            .execute()
            .modifiedEntity

    fun saveInputs(
        inputs: Iterable<Input<E>> ,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): List<E> =
        saveInputsCommand(inputs, block)
            .execute()
            .items.map { it.modifiedEntity }

    fun saveInputs(
        inputs: Iterable<Input<E>> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.REPLACE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): List<E> =
        saveInputsCommand(inputs, mode, associatedMode, block)
            .execute()
            .items.map { it.modifiedEntity }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entity) {...}.execute(fetcher)")
    )
    fun save(
        entity: E ,
        fetcher: Fetcher<E>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): E =
        saveCommand(entity, block)
            .execute(fetcher)
            .modifiedEntity

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entity, mode, associatedMode) {...}.execute(fetcher)")
    )
    fun save(
        entity: E ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        fetcher: Fetcher<E>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        saveCommand(entity, mode, associatedMode, block)
            .execute(fetcher)
            .modifiedEntity

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entities) {...}.execute(fetcher)")
    )
    fun saveEntities(
        entities: Iterable<E> ,
        fetcher: Fetcher<E>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): List<E> =
        saveEntitiesCommand(entities, block)
            .execute(fetcher)
            .items.map { it.modifiedEntity }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entities, mode, associatedMode) {...}.execute(fetcher)")
    )
    fun saveEntities(
        entities: Iterable<E> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        fetcher: Fetcher<E>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): List<E> =
        saveEntitiesCommand(entities, mode, associatedMode, block)
            .execute(fetcher)
            .items.map { it.modifiedEntity }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(input) {...}.execute(fetcher)")
    )
    fun save(
        input: Input<E> ,
        fetcher: Fetcher<E>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): E =
        saveCommand(input, block)
            .execute(fetcher)
            .modifiedEntity

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(input, mode, associatedMode) {...}.execute(fetcher)")
    )
    fun save(
        input: Input<E> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        fetcher: Fetcher<E>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        saveCommand(input, mode, associatedMode, block)
            .execute(fetcher)
            .modifiedEntity

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveInputsCommand(inputs) {...}.execute(fetcher)")
    )
    fun saveInputs(
        inputs: Iterable<Input<E>> ,
        fetcher: Fetcher<E>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): List<E> =
        saveInputsCommand(inputs, block)
            .execute(fetcher)
            .items.map { it.modifiedEntity }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entity, mode, associatedMode) {...}.execute(fetcher)")
    )
    fun saveInputs(
        inputs: Iterable<Input<E>> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        fetcher: Fetcher<E>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): List<E> =
        saveInputsCommand(inputs, mode, associatedMode, block)
            .execute(fetcher)
            .items.map { it.modifiedEntity }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entity) {...}.execute(viewType)")
    )
    fun <V: View<E>> save(
        entity: E ,
        viewType: KClass<V>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): V =
        saveCommand(entity, block)
            .execute(viewType)
            .modifiedView

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(entity, mode, associatedMode) {...}.execute(viewType)")
    )
    fun <V: View<E>> save(
        entity: E ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        viewType: KClass<V>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): V =
        saveCommand(entity, mode, associatedMode, block)
            .execute(viewType)
            .modifiedView

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveEntitiesCommand(entities) {...}.execute(viewType)")
    )
    fun <V: View<E>> saveEntities(
        entities: Iterable<E> ,
        viewType: KClass<V>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): List<V> =
        saveEntitiesCommand(entities, block)
            .execute(viewType)
            .viewItems.map { it.modifiedView }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveEntitiesCommand(entity, mode, associatedMode) {...}.execute(viewType)")
    )
    fun <V: View<E>> saveEntities(
        entities: Iterable<E> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        viewType: KClass<V>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): List<V> =
        saveEntitiesCommand(entities, mode, associatedMode, block)
            .execute(viewType)
            .viewItems.map { it.modifiedView }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(input) {...}.execute(viewType)")
    )
    fun <V: View<E>> save(
        input: Input<E> ,
        viewType: KClass<V>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): V =
        saveCommand(input, block)
            .execute(viewType)
            .modifiedView

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveCommand(input, mode, associatedMode) {...}.execute(viewType)")
    )
    fun <V: View<E>> save(
        input: Input<E> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        viewType: KClass<V>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): V =
        saveCommand(input, mode, associatedMode, block)
            .execute(viewType)
            .modifiedView

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveInputsCommand(inputs) {...}.execute(viewType)")
    )
    fun <V: View<E>> saveInputs(
        inputs: Iterable<Input<E>> ,
        viewType: KClass<V>,
        block: (KSaveCommandDsl.() -> Unit)? = null
    ): List<V> =
        saveInputsCommand(inputs, block)
            .execute(viewType)
            .viewItems.map { it.modifiedView }

    @Deprecated(
        "saving and re-fetching by fetcher/viewType is advanced feature, " +
                "please use `saveCommand`",
        replaceWith = ReplaceWith("saveInputsCommand(inputs, mode, associatedMode) {...}.execute(viewType)")
    )
    fun <V: View<E>> saveInputs(
        inputs: Iterable<Input<E>> ,
        mode: SaveMode,
        associatedMode: AssociatedSaveMode,
        viewType: KClass<V>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): List<V> =
        saveInputsCommand(inputs, mode, associatedMode, block)
            .execute(viewType)
            .viewItems.map { it.modifiedView }

    @Deprecated("Please use save", ReplaceWith(
        "save(input, SaveMode.INSERT_ONLY, associatedMode, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode"
    )
    )
    fun insert(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(input, SaveMode.INSERT_ONLY, associatedMode, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(entity, SaveMode.INSERT_ONLY, associatedMode, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode"
    )
    )
    fun insert(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(entity, SaveMode.INSERT_ONLY, associatedMode, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(input, SaveMode.INSERT_IF_ABSENT, associatedMode, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode"
    )
    )
    fun insertIfAbsent(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND_IF_ABSENT,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(input, SaveMode.INSERT_IF_ABSENT, associatedMode, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(entity, SaveMode.INSERT_IF_ABSENT, associatedMode, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode"
    )
    )
    fun insertIfAbsent(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.APPEND_IF_ABSENT,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(entity, SaveMode.INSERT_IF_ABSENT, associatedMode, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(input, SaveMode.UPDATE_ONLY, associatedMode, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode"
    )
    )
    fun update(
        input: Input<E>,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.UPDATE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(input, SaveMode.UPDATE_ONLY, associatedMode, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(entity, SaveMode.UPDATE_ONLY, associatedMode, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode"
    )
    )
    fun update(
        entity: E,
        associatedMode: AssociatedSaveMode = AssociatedSaveMode.UPDATE,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(entity, SaveMode.UPDATE_ONLY, associatedMode, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(input, SaveMode.UPSERT, AssociatedSaveMode.MERGE, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode",
        "org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode"
    )
    )
    fun merge(
        input: Input<E>,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(input, SaveMode.UPSERT, AssociatedSaveMode.MERGE, block)

    @Deprecated("Please use save", ReplaceWith(
        "save(entity, SaveMode.UPSERT, AssociatedSaveMode.MERGE, null, block)",
        "org.babyfish.jimmer.sql.ast.mutation.SaveMode",
        "org.babyfish.jimmer.sql.ast.mutation.AssociatedSaveMode"
    )
    )
    fun merge(
        entity: E,
        block: (KSaveCommandPartialDsl.() -> Unit)? = null
    ): E =
        save(entity, SaveMode.UPSERT, AssociatedSaveMode.MERGE, block)

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
