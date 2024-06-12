package io.quarkiverse.jimmer.runtime.kotlin

import org.babyfish.jimmer.sql.kt.KSqlClient

class UnConfiguredDataSourceQuarkusKSqlClientContainer(dataSourceName: String, private val message: String, private val cause: Throwable): QuarkusKSqlClientContainer(null, dataSourceName) {

    override val kSqlClient: KSqlClient
        get() = throw UnsupportedOperationException(message, cause)
}