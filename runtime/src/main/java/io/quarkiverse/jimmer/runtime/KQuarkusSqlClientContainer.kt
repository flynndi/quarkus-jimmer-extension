package io.quarkiverse.jimmer.runtime

import org.babyfish.jimmer.sql.kt.KSqlClient

class KQuarkusSqlClientContainer(kSqlClient: KSqlClient, dataSourceName: String) {

    var kSqlClient: KSqlClient? = null

    var dataSourceName: String? = null

    var id: String? = null
}