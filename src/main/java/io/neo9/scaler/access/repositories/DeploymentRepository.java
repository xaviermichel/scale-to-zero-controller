package io.neo9.scaler.access.repositories;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DeploymentRepository {

	private final KubernetesClient kubernetesClient;

	public DeploymentRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	public Optional<Deployment> findOne(String namespace, String name) {
		return Optional.ofNullable(
				kubernetesClient.apps().deployments()
						.inNamespace(namespace)
						.withName(name)
						.get()
		);
	}

	public Optional<Deployment> findOneByLabels(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.apps().deployments()
				.inNamespace(namespace)
				.withLabels(filteringLabels)
				.list().getItems().stream()
				.findFirst();
	}

	public Deployment scale(Deployment deployment, int count, boolean wait) {
		return kubernetesClient
				.apps().deployments()
				.inNamespace(deployment.getMetadata().getNamespace())
				.withName(deployment.getMetadata().getName())
				.scale(count, wait);
	}

	public List<Deployment> findAllInNamespace(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.apps().deployments()
				.inNamespace(namespace)
				.withLabels(filteringLabels)
				.list().getItems();
	}

	public List<Deployment> findAllInAnyNamespace(Map<String, String> filteringLabels) {
		return kubernetesClient.apps().deployments()
				.inAnyNamespace()
				.withLabels(filteringLabels)
				.list().getItems();
	}
}
