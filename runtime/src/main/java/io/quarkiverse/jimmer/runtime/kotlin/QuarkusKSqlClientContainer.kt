package io.quarkiverse.jimmer.runtime.kotlin

import org.babyfish.jimmer.sql.kt.KSqlClient

/** Compatibility facade for a datasource's CDI-managed client; it does not own client initialization. */
open class QuarkusKSqlClientContainer(open val kSqlClient: KSqlClient?, val dataSourceName: String) {

    val id: String = dataSourceName.replace("<", "").replace(">", "")
}