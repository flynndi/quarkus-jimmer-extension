package io.quarkiverse.jimmer.runtime

import io.quarkiverse.jimmer.runtime.cfg.JimmerBuildTimeConfig
import io.quarkus.arc.Arc
import io.quarkus.arc.ArcContainer
import jakarta.enterprise.event.Event
import org.babyfish.jimmer.sql.dialect.Dialect
import javax.sql.DataSource

class KQuarkusSqlClientProducer {

    private var config: JimmerBuildTimeConfig? = null

    private val container: ArcContainer = Arc.container()

    private var event: Event<Any>? = null

    fun KQuarkusSqlClientProducer(config: JimmerBuildTimeConfig?, event: Event<Any>?) {
        this.config = config
        this.event = event
    }
}