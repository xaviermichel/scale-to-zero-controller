package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
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
public class StatefulsetHijackingService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final EndpointSliceHijackingService endpointSliceHijackingService;

	private final EndpointSliceRepository endpointSliceRepository;

	private final PodRepository podRepository;

	public StatefulsetHijackingService(ScaleToZeroConfig scaleToZeroConfig, EndpointSliceHijackingService endpointSliceHijackingService, EndpointSliceRepository endpointSliceRepository, PodRepository podRepository) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.endpointSliceHijackingService = endpointSliceHijackingService;
		this.endpointSliceRepository = endpointSliceRepository;
		this.podRepository = podRepository;
	}

	/**
	 * Only release if :
	 *  * replicas count is more than 0
	 *  * all pods are in available replica
	 */
	public void releaseIfNecessary(StatefulSet statefulSet) throws MissingLabelException {
		Map<String, String> appIdentifierLabels = getLabelsValues(statefulSet, scaleToZeroConfig.getApplicationIdentifierLabels());
		List<EndpointSlice> endpointSlicesOfDeployment = endpointSliceRepository.findAllWithLabels(statefulSet.getMetadata().getNamespace(), appIdentifierLabels);

		if (expectMoreThanOneReplica(statefulSet) && allPodsAreInRunningPhase(statefulSet, appIdentifierLabels)) {
			log.info("releasing hijack on {}", getResourceNamespaceAndName(statefulSet));
			endpointSlicesOfDeployment.forEach(e -> endpointSliceHijackingService.releaseHijackedIfNecessary(e));
		}
	}

	private boolean expectMoreThanOneReplica(StatefulSet statefulSet) {
		return statefulSet.getSpec().getReplicas() > 0;
	}

	private boolean allPodsAreInRunningPhase(StatefulSet statefulSet, Map<String, String> appIdentifierLabels) {
		return statefulSet.getSpec().getReplicas().equals(podRepository.findAllWithLabelsInPhase(statefulSet.getMetadata().getNamespace(), appIdentifierLabels, "Running").size());
	}
}
