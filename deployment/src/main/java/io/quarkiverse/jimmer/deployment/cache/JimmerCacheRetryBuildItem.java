package io.quarkiverse.jimmer.deployment.cache;

import io.quarkus.builder.item.SimpleBuildItem;

/** The actual augmentation decision, independent of runtime schedule and scheduler configuration. */
public final class JimmerCacheRetryBuildItem extends SimpleBuildItem {

    private final boolean schedulerAvailable;
    private final boolean retryJobRegistered;

    public JimmerCacheRetryBuildItem(boolean schedulerAvailable, boolean retryJobRegistered) {
        this.schedulerAvailable = schedulerAvailable;
        this.retryJobRegistered = retryJobRegistered;
    }

    public boolean schedulerAvailable() {
        return schedulerAvailable;
    }

    public boolean retryJobRegistered() {
        return retryJobRegistered;
    }
}
