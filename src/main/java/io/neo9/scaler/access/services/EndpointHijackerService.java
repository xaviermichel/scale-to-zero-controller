package io.neo9.scaler.access.services;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.neo9.scaler.access.repositories.EndpointRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class EndpointHijackerService {

	private final EndpointRepository endpointRepository;

	public EndpointHijackerService(EndpointRepository endpointRepository) {
		this.endpointRepository = endpointRepository;
	}

	public void hijack(Endpoints endpoint) {
		String endpointNamespaceAndName = getResourceNamespaceAndName(endpoint);
		if (endpoint.getSubsets().isEmpty()) {
			log.info("I should hijack : {}", endpointNamespaceAndName);

			Optional<Endpoints> controllerEndpoint = endpointRepository.findControllerEndpoint();
			if (controllerEndpoint.isEmpty()) {
				log.warn("Did not find controller endpoint, cannot redirect traffic on me");
				return;
			}

			endpoint.setSubsets(controllerEndpoint.get().getSubsets());
			endpointRepository.patch(endpoint);
		}
	}

}
