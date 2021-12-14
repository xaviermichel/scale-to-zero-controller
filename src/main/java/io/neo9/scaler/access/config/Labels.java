package io.neo9.scaler.access.config;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Labels {

	public static final String IS_ALLOWED_TO_SCALE_LABEL_KEY = "scaling.neo9.io/is-scalable";
	public static final String IS_ALLOWED_TO_SCALE_LABEL_VALUE = "true";

	public static final String IS_SCALER_LABEL_KEY = "scaling.neo9.io/is-scaler";
	public static final String IS_SCALER_LABEL_VALUE = "true";
}
