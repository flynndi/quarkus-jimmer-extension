package io.quarkiverse.jimmer.runtime

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig
import io.quarkus.arc.ArcContainer
import jakarta.enterprise.event.Event
import org.babyfish.jimmer.sql.JSqlClient
import org.babyfish.jimmer.sql.dialect.Dialect
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.toKSqlClient
import javax.sql.DataSource

object SqlClients {

    @JvmStatic
    fun java(
        config: JimmerBuildTimeConfig,
        dataSource: DataSource?,
        dataSourceName: String,
        container: ArcContainer,
        event: Event<Any>,
        dialect: Dialect
    ): JSqlClient =
        JQuarkusSqlClient(config,
            dataSource,
            dataSourceName,
            container,
            event,
            dialect,
            false)

    @JvmStatic
    fun kotlin(
        config: JimmerBuildTimeConfig,
        dataSource: DataSource?,
        dataSourceName: String,
        container: ArcContainer,
        event: Event<Any>,
        dialect: Dialect
    ): KSqlClient =
        JQuarkusSqlClient(
            config,
            dataSource,
            dataSourceName,
            container,
            event,
            dialect,
            true
        ).toKSqlClient()
}