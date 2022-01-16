package io.neo9.scaler.access.repositories;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.utils.common.StringUtils.EMPTY;
import static java.util.stream.Collectors.toMap;

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

	public Deployment addToAnnotations(Deployment deployment, Map<String, String> annotations) {
		return kubernetesClient.apps().deployments()
				.inNamespace(deployment.getMetadata().getNamespace())
				.withName(deployment.getMetadata().getName())
				.edit(d -> new DeploymentBuilder(d).editMetadata()
						.addToAnnotations(annotations)
						.endMetadata()
						.build());
	}

	public Deployment removeFromAnnotations(Deployment deployment, List<String> annotationsKeys) {
		Map<String, String> annotations = annotationsKeys.stream().collect(toMap(key -> key, key -> EMPTY));
		return kubernetesClient.apps().deployments()
				.inNamespace(deployment.getMetadata().getNamespace())
				.withName(deployment.getMetadata().getName())
				.edit(d -> new DeploymentBuilder(d).editMetadata()
						.removeFromAnnotations(annotations)
						.endMetadata()
						.build());
	}
}
