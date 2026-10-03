package io.quarkiverse.jimmer.runtime

import io.quarkus.arc.ArcContainer
import org.babyfish.jimmer.sql.JSqlClient
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.cfg.KSqlClientDsl
import org.babyfish.jimmer.sql.kt.toKSqlClient
import java.util.function.Consumer
import javax.sql.DataSource

/**
 * Creates independent clients immediately, using the supplied Arc container's configuration and SPI beans.
 * These clients are not CDI beans; inject JSqlClient or KSqlClient for the managed datasource client.
 */
object SqlClients {

    @JvmStatic
    fun java(container: ArcContainer): JSqlClient =
        java(container, null, null)

    @JvmStatic
    fun java(container: ArcContainer, dataSource: DataSource?, dataSourceName: String?): JSqlClient =
        java(container, dataSource, dataSourceName, null)

    @JvmStatic
    fun java(container: ArcContainer, block: Consumer<JSqlClient.Builder>?): JSqlClient =
        java(container, null, null, block)

    @JvmStatic
    fun java(container: ArcContainer, dataSource: DataSource?, dataSourceName: String?, block: Consumer<JSqlClient.Builder>?): JSqlClient =
        QuarkusSqlClientFactory(container, dataSource, dataSourceName, block, false).create()

    @JvmStatic
    fun kotlin(container: ArcContainer): KSqlClient =
        kotlin(container, null, null)

    @JvmStatic
    fun kotlin(container: ArcContainer, dataSource: DataSource?, dataSourceName: String?): KSqlClient =
        kotlin(container, dataSource, dataSourceName, null)

    @JvmStatic
    fun kotlin(container: ArcContainer, block: (KSqlClientDsl.() -> Unit)?): KSqlClient =
        kotlin(container, null, null, block)

    @JvmStatic
    fun kotlin(container: ArcContainer, dataSource: DataSource?, dataSourceName: String?, block: (KSqlClientDsl.() -> Unit)?): KSqlClient =
        QuarkusSqlClientFactory(container, dataSource, dataSourceName, block?.let { Consumer { KSqlClientDsl(it).block()} }, true).create().toKSqlClient()
}