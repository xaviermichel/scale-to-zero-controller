package io.neo9.scaler.access.controllers.kubernetes;

import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSliceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.neo9.scaler.access.services.EndpointSliceHijackingService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Component
@Slf4j
public class ApplicationsEndpointSliceController extends ReconnectableSingleWatcher<EndpointSlice, EndpointSliceList> {

	private final KubernetesClient kubernetesClient;

	public ApplicationsEndpointSliceController(KubernetesClient kubernetesClient, EndpointSliceHijackingService endpointSliceHijackingService) {
		super("endpointSlice-onApplicationsEndpoints", (action, endpointSlice) -> {
			String endpointNamespaceAndName = getResourceNamespaceAndName(endpointSlice);
			log.trace("start process event on {}", endpointNamespaceAndName);
			switch (action) {
				case ADDED:
				case MODIFIED:
					log.info("update event detected for {}", endpointNamespaceAndName);
					endpointSliceHijackingService.hijackIfNecessary(endpointSlice);
					break;
				default:
					// do nothing on deletion
					break;
			}
			log.trace("end of process event on {}", endpointNamespaceAndName);
			return null;
		});
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public FilterWatchListDeletable<EndpointSlice, EndpointSliceList> watch() {
		return kubernetesClient.discovery().v1beta1().endpointSlices()
				.inAnyNamespace()
				.withLabel(IS_ALLOWED_TO_SCALE_LABEL_KEY, IS_ALLOWED_TO_SCALE_LABEL_VALUE);
	}

}
