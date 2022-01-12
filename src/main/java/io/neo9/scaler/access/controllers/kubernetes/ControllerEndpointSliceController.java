package io.neo9.scaler.access.controllers.kubernetes;

import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSliceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.neo9.scaler.access.services.EndpointSliceHijackingService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_SCALER_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Component
@Slf4j
public class ControllerEndpointSliceController extends ReconnectableSingleWatcher<EndpointSlice, EndpointSliceList> {

	public ControllerEndpointSliceController(KubernetesClient kubernetesClient, EndpointSliceHijackingService endpointSliceHijackingService) {
		super(
				/* unique name */
				"endpointSlice-onControllerEndpoints",
				/* watch what */
				kubernetesClient.discovery().v1beta1().endpointSlices()
						.inAnyNamespace()
						.withLabel(IS_SCALER_LABEL_KEY, TRUE),
				/* on event */
				(action, endpointSlice) -> {
					String endpointNamespaceAndName = getResourceNamespaceAndName(endpointSlice);
					log.trace("start process event on {}", endpointNamespaceAndName);
					switch (action) {
						case ADDED:
						case MODIFIED:
							log.info("update event detected for {}", endpointNamespaceAndName);
							endpointSliceHijackingService.reconcileEndpointSliceWithNewControllerEndpointSlice();
							break;
						default:
							// do nothing on deletion
							break;
					}
					log.trace("end of process event on {}", endpointNamespaceAndName);
					return null;
				}
		);
	}
}
