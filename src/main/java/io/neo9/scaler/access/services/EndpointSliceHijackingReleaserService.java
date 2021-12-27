package io.neo9.scaler.access.services;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_CLOUD_PROVIDER_CONTROLLER_VALUE;
import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE;
import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class EndpointSliceHijackingReleaserService {

	private final EndpointSliceRepository endpointSliceRepository;

	public EndpointSliceHijackingReleaserService(EndpointSliceRepository endpointSliceRepository) {
		this.endpointSliceRepository = endpointSliceRepository;
	}

	public void releaseHijacked(String namespace, Map<String, String> applicationIdentifierLabels) {
		Optional<EndpointSlice> endpointSliceOpt = endpointSliceRepository.findOneByLabels(namespace, applicationIdentifierLabels);
		if (endpointSliceOpt.isEmpty()) {
			log.warn("did not find the endpointslice in namespace {}, with labels {}, can't release hijacking", namespace, applicationIdentifierLabels);
			return;
		}
		releaseHijacked(endpointSliceOpt.get());
	}

	public void releaseHijacked(EndpointSlice appEndpointSlice) {
		if (!ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE.equals(getLabelValue(ENDPOINT_SLICE_MANAGED_BY_KEY, appEndpointSlice))) {
			log.warn("Cannot release an non hijacked endpoint : {}", getResourceNamespaceAndName(appEndpointSlice));
			return;
		}
		appEndpointSlice.getMetadata().getLabels().put(ENDPOINT_SLICE_MANAGED_BY_KEY, ENDPOINT_SLICE_MANAGED_BY_CLOUD_PROVIDER_CONTROLLER_VALUE);
		endpointSliceRepository.patch(appEndpointSlice);
	}

}
