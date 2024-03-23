package io.quarkiverse.jimmer.runtime

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig
import io.quarkus.arc.ArcContainer
import jakarta.enterprise.event.Event
import org.babyfish.jimmer.sql.JSqlClient
import org.babyfish.jimmer.sql.dialect.Dialect
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.cfg.KSqlClientDsl
import org.babyfish.jimmer.sql.kt.toKSqlClient
import java.util.function.Consumer
import javax.sql.DataSource

object SqlClients {

//    @JvmStatic
//    fun java(
//        ctx: ApplicationContext
//    ): JSqlClient =
//        java(ctx, null, null)
//
//    @JvmStatic
//    fun java(
//        ctx: ApplicationContext,
//        dataSource: DataSource?
//    ): JSqlClient =
//        java(ctx, dataSource, null)
//
//    @JvmStatic
//    fun java(
//        ctx: ApplicationContext,
//        block: Consumer<JSqlClient.Builder>?
//    ): JSqlClient =
//        java(ctx, null, block)
//
//    @JvmStatic
//    fun java(
//        ctx: ApplicationContext,
//        dataSource: DataSource?,
//        block: Consumer<JSqlClient.Builder>?
//    ): JSqlClient =
//        JSpringSqlClient(ctx, dataSource, block, false)
//
//    @JvmStatic
//    fun kotlin(ctx: ApplicationContext): KSqlClient =
//        kotlin(ctx, null, null)
//
//    @JvmStatic
//    fun kotlin(
//        ctx: ApplicationContext,
//        dataSource: DataSource?
//    ): KSqlClient =
//        kotlin(ctx, dataSource, null)
//
//    @JvmStatic
//    fun kotlin(
//        ctx: ApplicationContext,
//        block: (KSqlClientDsl.() -> Unit)?
//    ): KSqlClient =
//        kotlin(ctx, null, block)

    @JvmStatic
    fun java(
        config: JimmerBuildTimeConfig,
        dataSource: DataSource?,
        dataSourceName: String,
        container: ArcContainer,
        block: (KSqlClientDsl.() -> Unit)?,
        event: Event<Any>,
        dialect: Dialect,
        isKotlin: Boolean
    ): JQuarkusSqlClient =
        JQuarkusSqlClient(config,
            dataSource,
            dataSourceName,
            container,
            block?.let {
                Consumer {
                    KSqlClientDsl(it).block()
                }
            },
            event,
            dialect,
            isKotlin)

    @JvmStatic
    fun kotlin(
        config: JimmerBuildTimeConfig,
        dataSource: DataSource?,
        dataSourceName: String,
        container: ArcContainer,
        block: (KSqlClientDsl.() -> Unit)?,
        event: Event<Any>,
        dialect: Dialect
    ): KSqlClient =
        JQuarkusSqlClient(
            config,
            dataSource,
            dataSourceName,
            container,
            block?.let {
                Consumer {
                    KSqlClientDsl(it).block()
                }
            },
            event,
            dialect,
            true
        ).toKSqlClient()
}