package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelsValues;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class DeploymentHijackingService {

	private final EndpointSliceHijackingService endpointSliceHijackingService;

	private final EndpointSliceRepository endpointSliceRepository;

	private final ScaleToZeroConfig scaleToZeroConfig;

	public DeploymentHijackingService(EndpointSliceHijackingService endpointSliceHijackingService, EndpointSliceRepository endpointSliceRepository, ScaleToZeroConfig scaleToZeroConfig) {
		this.endpointSliceHijackingService = endpointSliceHijackingService;
		this.endpointSliceRepository = endpointSliceRepository;
		this.scaleToZeroConfig = scaleToZeroConfig;
	}

	public void releaseIfNecessary(Deployment deployment) throws MissingLabelException {
		Map<String, String> appIdentifierLabels = getLabelsValues(deployment, scaleToZeroConfig.getApplicationIdentifierLabels());
		List<EndpointSlice> endpointSlicesOfDeployment = endpointSliceRepository.findAllWithLabels(deployment.getMetadata().getNamespace(), appIdentifierLabels);

		boolean shouldReleaseHijack = ! deployment.getSpec().getReplicas().equals(0);
		if (shouldReleaseHijack) {
			log.info("releasing hijack on {}", getResourceNamespaceAndName(deployment));
			endpointSlicesOfDeployment.forEach(e -> endpointSliceHijackingService.releaseHijackedIfNecessary(e));
		}
	}
}
