package io.neo9.scaler.access.repositories;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Labels.KUBERNETES_LABEL_INSTANCE_CONTROLLER_VALUE;
import static io.neo9.scaler.access.config.Labels.KUBERNETES_LABEL_INSTANCE_KEY;

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
				.withLabel(KUBERNETES_LABEL_INSTANCE_KEY, KUBERNETES_LABEL_INSTANCE_CONTROLLER_VALUE).list().getItems()
				.stream()
				.findFirst();
	}

	public Optional<EndpointSlice> findOneByLabels(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.discovery().v1beta1().endpointSlices()
				.inNamespace(namespace)
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
