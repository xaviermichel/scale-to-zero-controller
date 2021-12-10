package io.neo9.scaler.access.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.neo9.scaler.access.repositories.EndpointRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class EndpointHijackerService {

	private final EndpointRepository endpointRepository;

	private final ServiceRepository serviceRepository;

	public EndpointHijackerService(EndpointRepository endpointRepository, ServiceRepository serviceRepository) {
		this.endpointRepository = endpointRepository;
		this.serviceRepository = serviceRepository;
	}

	public void hijack(Endpoints endpoint) {
		String endpointNamespaceAndName = getResourceNamespaceAndName(endpoint);
		if (endpoint.getSubsets().isEmpty()) {
			log.info("i'm hijacking : {}", endpointNamespaceAndName);

			Optional<Endpoints> controllerEndpoint = endpointRepository.findControllerEndpoint();
			if (controllerEndpoint.isEmpty()) {
				log.warn("did not find controller endpoint, cannot redirect traffic on me");
				return;
			}

			endpoint.setSubsets(controllerEndpoint.get().getSubsets());

			io.fabric8.kubernetes.api.model.Service service = serviceRepository.find(endpoint.getMetadata().getNamespace(), endpoint.getMetadata().getName());
			List<EndpointPort> endpointPorts = new ArrayList<>();
			for (ServicePort servicePort : service.getSpec().getPorts()) {
				endpointPorts.add(new EndpointPortBuilder()
										.withName(servicePort.getName())
										.withPort(servicePort.getPort())
										.withProtocol(servicePort.getProtocol())
										.withAppProtocol(servicePort.getAppProtocol())
										.build());
			}
			// TODO: for now, I assume that we have only one instance of hijacker
			endpoint.getSubsets().get(0).setPorts(endpointPorts);

			endpointRepository.patch(endpoint);
		}
	}

}
