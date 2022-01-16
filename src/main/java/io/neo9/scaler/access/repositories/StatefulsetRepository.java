package io.neo9.scaler.access.repositories;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.utils.common.StringUtils.EMPTY;
import static java.util.stream.Collectors.toMap;

@Component
@Slf4j
public class StatefulsetRepository {

	private final KubernetesClient kubernetesClient;

	public StatefulsetRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	public Optional<StatefulSet> findOne(String namespace, String name) {
		return Optional.ofNullable(
				kubernetesClient.apps().statefulSets()
						.inNamespace(namespace)
						.withName(name)
						.get()
		);
	}

	public Optional<StatefulSet> findOneByLabels(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.apps().statefulSets()
				.inNamespace(namespace)
				.withLabels(filteringLabels)
				.list().getItems().stream()
				.findFirst();
	}

	public StatefulSet scale(StatefulSet statefulSet, int count, boolean wait) {
		return kubernetesClient
				.apps().statefulSets()
				.inNamespace(statefulSet.getMetadata().getNamespace())
				.withName(statefulSet.getMetadata().getName())
				.scale(count, wait);
	}

	public List<StatefulSet> findAllInNamespace(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.apps().statefulSets()
				.inNamespace(namespace)
				.withLabels(filteringLabels)
				.list().getItems();
	}

	public List<StatefulSet> findAllInAnyNamespace(Map<String, String> filteringLabels) {
		return kubernetesClient.apps().statefulSets()
				.inAnyNamespace()
				.withLabels(filteringLabels)
				.list().getItems();
	}

	public StatefulSet addToAnnotations(StatefulSet statefulSet, Map<String, String> annotations) {
		return kubernetesClient.apps().statefulSets()
				.inNamespace(statefulSet.getMetadata().getNamespace())
				.withName(statefulSet.getMetadata().getName())
				.edit(s -> new StatefulSetBuilder(s).editMetadata()
						.addToAnnotations(annotations)
						.endMetadata()
						.build());
	}

	public StatefulSet removeFromAnnotations(StatefulSet statefulSet, List<String> annotationsKeys) {
		Map<String, String> annotations = annotationsKeys.stream().collect(toMap(key -> key, key -> EMPTY));
		return kubernetesClient.apps().statefulSets()
				.inNamespace(statefulSet.getMetadata().getNamespace())
				.withName(statefulSet.getMetadata().getName())
				.edit(s -> new StatefulSetBuilder(s).editMetadata()
						.removeFromAnnotations(annotations)
						.endMetadata()
						.build());
	}
}
