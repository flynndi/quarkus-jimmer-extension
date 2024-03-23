package io.quarkiverse.jimmer.runtime.kotlin

import org.babyfish.jimmer.sql.kt.KSqlClient

class KQuarkusSqlClientContainer() {

    var kSqlClient: KSqlClient? = null

    var dataSourceName: String? = null

    var id: String? = null

    constructor(kSqlClient: KSqlClient?, dataSourceName: String?) : this() {
        this.kSqlClient = kSqlClient
        this.dataSourceName = dataSourceName
        if (dataSourceName != null) {
            this.id = dataSourceName.replace("<", "").replace(">", "")
        }
    }
}