package io.neo9.scaler.access.repositories;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class EndpointSliceRepository {

	private final KubernetesClient kubernetesClient;

	public EndpointSliceRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	public Optional<EndpointSlice> findControllerEndpointSlice() {
		return kubernetesClient.discovery().v1beta1().endpointSlices()
				.inAnyNamespace()
				.withLabel("app.kubernetes.io/instance", "scale-to-zero-controller").list().getItems() // TODO : pass as constant
				.stream()
				.findFirst();
	}

	public Optional<EndpointSlice> findOneByLabels(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.discovery().v1beta1().endpointSlices()
				.withLabels(filteringLabels)
				.list().getItems().stream()
				.findFirst();
	}

	public List<EndpointSlice> findAllWithLabels(Map<String, String> filteringLabels) {
		return kubernetesClient.discovery().v1beta1().endpointSlices()
				.inAnyNamespace()
				.withLabels(filteringLabels)
				.list().getItems();
	}

	public EndpointSlice patch(EndpointSlice endpoint) {
		return kubernetesClient.discovery().v1beta1().endpointSlices()
				.inNamespace(endpoint.getMetadata().getNamespace())
				.withName(endpoint.getMetadata().getName())
				.patch(endpoint);
	}

}
