package io.quarkiverse.jimmer.it.config;

import jakarta.enterprise.context.ApplicationScoped;

import org.babyfish.jimmer.sql.filter.Filter;
import org.babyfish.jimmer.sql.filter.FilterArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkiverse.jimmer.it.entity.TenantAwareProps;
import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class TenantFilter implements Filter<TenantAwareProps> {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    @Override
    public void filter(FilterArgs<TenantAwareProps> args) {
        LOGGER.info("args: {}", args);
        LOGGER.info("args.getTable(): {}", args.getTable());
        args.where(args.getTable().tenant().eq("a"));
    }
}
