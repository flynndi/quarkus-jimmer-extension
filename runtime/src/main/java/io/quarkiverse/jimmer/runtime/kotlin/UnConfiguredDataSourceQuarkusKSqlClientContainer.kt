package io.quarkiverse.jimmer.runtime.kotlin

import org.babyfish.jimmer.sql.kt.KSqlClient

class UnConfiguredDataSourceQuarkusKSqlClientContainer() : QuarkusKSqlClientContainer() {

    private var message: String? = null

    private var cause: Throwable? = null

    constructor(dataSourceName: String, message: String, cause: Throwable) : this() {
        this.dataSourceName = dataSourceName
        this.message = message
        this.cause = cause
    }

    override var kSqlClient: KSqlClient?
        get() = throw UnsupportedOperationException(message, cause)
        set(value) {}
}