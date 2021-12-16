package io.neo9.scaler.access.utils.common;

import java.util.Map;

import javax.annotation.Nullable;

import io.fabric8.kubernetes.api.model.HasMetadata;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KubernetesUtils {

	public static String getAnnotationValue(String key, HasMetadata hasMetadata, String defaultValue) {
		Map<String, String> annotations = hasMetadata.getMetadata().getAnnotations();
		if (annotations == null) {
			return defaultValue;
		}
		return annotations.getOrDefault(key, defaultValue);
	}

	@Nullable
	public static String getAnnotationValue(String key, HasMetadata hasMetadata) {
		return hasMetadata.getMetadata().getAnnotations().get(key);
	}

	public static String getLabelValue(String key, HasMetadata hasMetadata, String defaultValue) {
		Map<String, String> labels = hasMetadata.getMetadata().getLabels();
		if (labels == null) {
			return defaultValue;
		}
		return labels.getOrDefault(key, defaultValue);
	}

	@Nullable
	public static String getLabelValue(String key, HasMetadata hasMetadata) {
		Map<String, String> labels = hasMetadata.getMetadata().getLabels();
		if (labels == null) {
			return null;
		}
		return labels.get(key);
	}

	public static String getResourceNamespaceAndName(HasMetadata hasMetadata) {
		String namespace = hasMetadata.getMetadata().getNamespace();
		String name = hasMetadata.getMetadata().getName();
		return String.format("%s/%s", namespace, name);
	}
}
