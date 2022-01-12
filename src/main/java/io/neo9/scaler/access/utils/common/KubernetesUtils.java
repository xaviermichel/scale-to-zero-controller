package io.neo9.scaler.access.utils.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import lombok.experimental.UtilityClass;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.springframework.util.StringUtils.uncapitalize;

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
		Map<String, String> annotations = hasMetadata.getMetadata().getAnnotations();
		if (annotations == null) {
			return null;
		}
		return annotations.get(key);
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
		return String.format("%s/%s/%s", uncapitalize(hasMetadata.getKind()), namespace, name);
	}

	public static Map<String, String> getLabelsValues(HasMetadata hasMetadata, Set<String> labelKeys) throws MissingLabelException {
		Map<String, String> labels = new HashMap<>();
		for (String labelKey : labelKeys) {
			String sourceLabelValue = getLabelValue(labelKey, hasMetadata);
			if (isEmpty(sourceLabelValue)) {
				throw new MissingLabelException(labelKey, hasMetadata);
			}
			labels.put(labelKey, sourceLabelValue);
		}
		return labels;
	}

}
