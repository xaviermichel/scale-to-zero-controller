package io.neo9.scaler.access.repositories;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EndpointRepository {

	private final KubernetesClient kubernetesClient;

	public EndpointRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	public Optional<Endpoints> findControllerEndpoint() {
		return kubernetesClient.endpoints().inAnyNamespace()
				.withLabel("app.kubernetes.io/instance", "scale-to-zero-controller").list().getItems()
				.stream()
				.findFirst();
	}

	public Endpoints patch(Endpoints endpoint) {
		return kubernetesClient
				.endpoints()
				.inNamespace(endpoint.getMetadata().getNamespace())
				.withName(endpoint.getMetadata().getName())
				.patch(endpoint);
	}

}
