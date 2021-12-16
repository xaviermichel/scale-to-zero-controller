package io.neo9.scaler.access.repositories;

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

	public Optional<Deployment> findOneByLabels(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.apps().deployments()
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
}
