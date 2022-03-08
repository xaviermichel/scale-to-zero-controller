package io.neo9.scaler.access.config;

import lombok.experimental.UtilityClass;

import java.util.Set;

@UtilityClass
public class Annotations {

    public static final String BEFORE_SCALE_REQUIREMENTS = "scaling.neo9.io/before-scale-requirements";

    public static final String SHOW_SPLASH_SCREEN = "scaling.neo9.io/show-splash-screen";

    public static final String DOWNSCALE_LOG_STRATEGY = "scaling.neo9.io/scale-down-after-log-activity-delay-in-minutes";

    public static final String DOWNSCALE_LOG_STRATEGY_EXCLUDE_PATTERN = "scaling.neo9.io/scale-down-exclude-log-activity";

    public static final Set<String> ALL_DOWNSCALE_STRATEGY = Set.of(DOWNSCALE_LOG_STRATEGY);

    public static final String ORIGINAL_REPLICA = "scaling.neo9.io/original-replicas";

    public static final String INTERCEPT_AS_TCP = "scaling.neo9.io/use-tcp-interceptor";
}
