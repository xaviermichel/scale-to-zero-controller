package io.neo9.scaler.access.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.neo9.scaler.access.repositories.EndpointRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
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

	public void hijack(Endpoints appEndpoint) {
		Optional<Endpoints> controllerEndpointOpt = endpointRepository.findControllerEndpoint();
		if (controllerEndpointOpt.isEmpty()) {
			log.warn("did not find controller endpoint, cannot redirect traffic on me");
			return;
		}
		Endpoints controllerEndpoint = controllerEndpointOpt.get();

		String endpointNamespaceAndName = getResourceNamespaceAndName(appEndpoint);
		if (appEndpoint.getSubsets().isEmpty() || appEndpointIsPointingOnOldController(appEndpoint, controllerEndpoint)) {
			log.info("i'm hijacking : {}", endpointNamespaceAndName);

			appEndpoint.setSubsets(controllerEndpoint.getSubsets());

			io.fabric8.kubernetes.api.model.Service service = serviceRepository.find(appEndpoint.getMetadata().getNamespace(), appEndpoint.getMetadata().getName());
			List<EndpointPort> endpointPorts = new ArrayList<>();
			for (ServicePort servicePort : service.getSpec().getPorts()) {
				endpointPorts.add(new EndpointPortBuilder()
						.withName(servicePort.getName())
						.withPort(servicePort.getPort())
						.withProtocol(servicePort.getProtocol())
						.withAppProtocol(servicePort.getAppProtocol())
						.build());
			}

			for (EndpointSubset appEndpointSubset : appEndpoint.getSubsets()) {
				appEndpointSubset.setPorts(endpointPorts);
			}

			endpointRepository.patch(appEndpoint);
		}
	}

	private boolean appEndpointIsPointingOnOldController(Endpoints appEndpoint, Endpoints controllerEndpoint) {
		// verify if we point on an instance of controller
		if (appEndpoint.getSubsets().get(0).getAddresses().isEmpty()) {
			log.warn("empty addresses for app endpoint");
			return false;
		}
		ObjectReference appTargetRef = appEndpoint.getSubsets().get(0).getAddresses().get(0).getTargetRef();
		if (!appTargetRef.getName().contains("scale-to-zero-controller")) {
			return false;
		}

		// verify if the pointed instance is the current one
		if (controllerEndpoint.getSubsets().get(0).getAddresses().isEmpty()) {
			log.warn("empty addresses for controller endpoint");
			return false;
		}
		ObjectReference controllerTargetRef = controllerEndpoint.getSubsets().get(0).getAddresses().get(0).getTargetRef();
		if (controllerTargetRef.getName().equals(appTargetRef.getName())) {
			return false;
		}

		// we have to update endpoint
		return true;
	}

	public void reconcileEndpointsWithNewControllerEndpoint(Endpoints endpoint) {
		log.info("controller endpoint changed, starting listeners update");
		endpointRepository.findAllWithLabels(Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, IS_ALLOWED_TO_SCALE_LABEL_VALUE)).forEach(appEndpoint -> hijack(appEndpoint));
		log.info("controller endpoint changed, end of listeners update");
	}
}
