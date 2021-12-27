package io.neo9.scaler.access.config;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Labels {

	public static final String IS_ALLOWED_TO_SCALE_LABEL_KEY = "scaling.neo9.io/is-scalable";

	public static final String IS_ALLOWED_TO_SCALE_LABEL_VALUE = "true";

	public static final String IS_SCALER_LABEL_KEY = "scaling.neo9.io/is-scaler";

	public static final String IS_SCALER_LABEL_VALUE = "true";

	public static final String ENDPOINT_SLICE_MANAGED_BY_KEY = "endpointslice.kubernetes.io/managed-by";

	public static final String ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE = "scale-to-zero-controller";

	public static final String ENDPOINT_SLICE_MANAGED_BY_CLOUD_PROVIDER_CONTROLLER_VALUE = "endpointslice-controller.k8s.io";

	public static final String KUBERNETES_LABEL_INSTANCE_KEY = "app.kubernetes.io/instance";

	public static final String KUBERNETES_LABEL_INSTANCE_CONTROLLER_VALUE = "scale-to-zero-controller";
}
