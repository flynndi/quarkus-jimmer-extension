package io.quarkiverse.jimmer.runtime.kotlin

import org.babyfish.jimmer.sql.kt.KSqlClient

open class QuarkusKSqlClientContainer(open val kSqlClient: KSqlClient?, val dataSourceName: String) {

    val id: String = dataSourceName.replace("<", "").replace(">", "")
}