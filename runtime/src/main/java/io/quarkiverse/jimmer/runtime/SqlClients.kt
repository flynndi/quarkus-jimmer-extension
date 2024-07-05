package io.quarkiverse.jimmer.runtime

import org.babyfish.jimmer.sql.JSqlClient
import org.babyfish.jimmer.sql.dialect.Dialect
import org.babyfish.jimmer.sql.kt.KSqlClient
import org.babyfish.jimmer.sql.kt.toKSqlClient
import javax.sql.DataSource

object SqlClients {

    @JvmStatic
    fun java(
        dataSource: DataSource?,
        dataSourceName: String,
        dialect: Dialect
    ): JSqlClient =
        JQuarkusSqlClient(
            dataSource,
            dataSourceName,
            dialect,
            false)

    @JvmStatic
    fun kotlin(
        dataSource: DataSource?,
        dataSourceName: String,
        dialect: Dialect
    ): KSqlClient =
        JQuarkusSqlClient(
            dataSource,
            dataSourceName,
            dialect,
            true
        ).toKSqlClient()
}