package io.neo9.scaler.access.repositories;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StatefulsetRepository {

	private final KubernetesClient kubernetesClient;

	public StatefulsetRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
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
}
