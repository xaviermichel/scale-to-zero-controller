package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelsValues;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class DeploymentHijackingService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final EndpointSliceHijackingService endpointSliceHijackingService;

	private final EndpointSliceRepository endpointSliceRepository;

	private final PodRepository podRepository;

	public DeploymentHijackingService(ScaleToZeroConfig scaleToZeroConfig, EndpointSliceHijackingService endpointSliceHijackingService, EndpointSliceRepository endpointSliceRepository, PodRepository podRepository) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.endpointSliceHijackingService = endpointSliceHijackingService;
		this.endpointSliceRepository = endpointSliceRepository;
		this.podRepository = podRepository;
	}

	/**
	 * Only release if :
	 *  * replicas count is more than 0
	 *  * there is at least one available replica
	 */
	public void releaseIfNecessary(Deployment deployment) throws MissingLabelException {
		Map<String, String> appIdentifierLabels = getLabelsValues(deployment, scaleToZeroConfig.getApplicationIdentifierLabels());
		List<EndpointSlice> endpointSlicesOfDeployment = endpointSliceRepository.findAllWithLabels(deployment.getMetadata().getNamespace(), appIdentifierLabels);

		if (expectMoreThanOneReplica(deployment) && atLeastOnePodIsInReadyState(deployment, appIdentifierLabels)) {
			log.info("releasing hijack on {}", getResourceNamespaceAndName(deployment));
			endpointSlicesOfDeployment.forEach(e -> endpointSliceHijackingService.releaseHijackedIfNecessary(e));
		}
	}

	private boolean expectMoreThanOneReplica(Deployment deployment) {
		return deployment.getSpec().getReplicas() > 0;
	}

	private boolean atLeastOnePodIsInReadyState(Deployment deployment, Map<String, String> appIdentifierLabels) {
		return podRepository.findAllWithLabelsInPhase(deployment.getMetadata().getNamespace(), appIdentifierLabels, "Running").size() > 0;
	}
}
