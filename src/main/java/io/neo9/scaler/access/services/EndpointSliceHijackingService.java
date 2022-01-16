package io.neo9.scaler.access.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointPort;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.controllers.tcp.DynamicRequestHandler;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_CLOUD_PROVIDER_CONTROLLER_VALUE;
import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE;
import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getWorkloadIdentifierLabels;
import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@Slf4j
public class EndpointSliceHijackingService {

	private final static String ENDPOINT_SLICE_ADDRESS_TYPE_IPV4 = "IPv4";

	// not trivial, dynamically starts tcp handlers on the right port when a deployment is scaled to 0
	private final DynamicRequestHandler dynamicRequestHandler;

	private final EndpointSliceRepository endpointSliceRepository;

	private final ServiceRepository serviceRepository;

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final WorkloadService workloadService;

	public EndpointSliceHijackingService(DynamicRequestHandler dynamicRequestHandler, EndpointSliceRepository endpointSliceRepository, ServiceRepository serviceRepository, ScaleToZeroConfig scaleToZeroConfig, WorkloadService workloadService) {
		this.dynamicRequestHandler = dynamicRequestHandler;
		this.endpointSliceRepository = endpointSliceRepository;
		this.serviceRepository = serviceRepository;
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.workloadService = workloadService;
	}

	private boolean isIPV4EndpointSlice(EndpointSlice appEndpointSlice) {
		return ENDPOINT_SLICE_ADDRESS_TYPE_IPV4.equals(appEndpointSlice.getAddressType());
	}

	public void hijackIfNecessary(EndpointSlice appEndpointSlice) {
		if (!isIPV4EndpointSlice(appEndpointSlice)) {
			log.warn("only ipv4 traffic control is implemented, cannot work on {}", getResourceNamespaceAndName(appEndpointSlice));
			return;
		}

		Optional<EndpointSlice> controllerEndpointSliceOpt = endpointSliceRepository.findControllerEndpointSlice();
		if (controllerEndpointSliceOpt.isEmpty()) {
			log.warn("did not find controller endpointslice, cannot redirect traffic on me");
			return;
		}
		EndpointSlice controllerEndpointSlice = controllerEndpointSliceOpt.get();

		String endpointNamespaceAndName = getResourceNamespaceAndName(appEndpointSlice);
		if (isEmpty(appEndpointSlice.getEndpoints()) || appEndpointSliceIsPointingOnOldController(appEndpointSlice, controllerEndpointSlice)) {
			log.info("i'm hijacking : {}", endpointNamespaceAndName);

			// update endpoint to point on controller
			appEndpointSlice.setEndpoints(controllerEndpointSlice.getEndpoints());


			// update port to match witch controller target
			io.fabric8.kubernetes.api.model.Service appService = serviceRepository.find(
					appEndpointSlice.getMetadata().getNamespace(),
					appEndpointSlice.getMetadata().getOwnerReferences().get(0).getName()
			);
			List<EndpointPort> endpointPorts = new ArrayList<>();
			for (ServicePort servicePort : appService.getSpec().getPorts()) {
				endpointPorts.add(new EndpointPortBuilder()
						.withName(servicePort.getName())
						.withPort(servicePort.getPort())
						.withProtocol(servicePort.getProtocol())
						.withAppProtocol(servicePort.getAppProtocol())
						.build());

				dynamicRequestHandler.startTcpServer(servicePort.getPort());
			}
			appEndpointSlice.setPorts(endpointPorts);

			// flag endpoint as managed by controller
			appEndpointSlice.getMetadata().getLabels().put(ENDPOINT_SLICE_MANAGED_BY_KEY, ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE);

			endpointSliceRepository.patch(appEndpointSlice);

			Map<String, String> applicationsIdentifierLabels = getWorkloadIdentifierLabels(appEndpointSlice, scaleToZeroConfig.getApplicationIdentifierLabels());
			workloadService.annotate(workloadService.getWorkload(appEndpointSlice.getMetadata().getNamespace(), applicationsIdentifierLabels), scaleToZeroConfig.getOnHijackAnnotationsToAdd());
		}
	}

	private boolean appEndpointSliceIsPointingOnOldController(EndpointSlice appEndpointSlice, EndpointSlice controllerEndpointSlice) {
		if (isEmpty(appEndpointSlice.getEndpoints())) {
			log.warn("empty addresses for app endpoint");
			return false;
		}
		if (isEmpty(controllerEndpointSlice.getEndpoints())) {
			log.warn("empty addresses for controller endpoint");
			return false;
		}

		// verify if we point on an instance of controller
		if (!ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE.equals(getLabelValue(ENDPOINT_SLICE_MANAGED_BY_KEY, appEndpointSlice))) {
			return false;
		}

		// verify if the pointed instance is the current one
		ObjectReference controllerTargetRef = controllerEndpointSlice.getEndpoints().get(0).getTargetRef();
		ObjectReference appTargetRef = appEndpointSlice.getEndpoints().get(0).getTargetRef();
		if (controllerTargetRef.getName().equals(appTargetRef.getName())) {
			return false;
		}

		// we have to update endpointslice
		return true;
	}

	public void releaseHijackedIfNecessary(EndpointSlice appEndpointSlice) {
		if (!ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE.equals(getLabelValue(ENDPOINT_SLICE_MANAGED_BY_KEY, appEndpointSlice))) {
			log.debug("cannot release an non hijacked endpoint : {}", getResourceNamespaceAndName(appEndpointSlice));
			return;
		}
		if (!isIPV4EndpointSlice(appEndpointSlice)) {
			log.warn("only ipv4 traffic control is implemented, cannot work on {}", getResourceNamespaceAndName(appEndpointSlice));
			return;
		}
		appEndpointSlice.getMetadata().getLabels().put(ENDPOINT_SLICE_MANAGED_BY_KEY, ENDPOINT_SLICE_MANAGED_BY_CLOUD_PROVIDER_CONTROLLER_VALUE);
		endpointSliceRepository.patch(appEndpointSlice);
	}

	public void reconcileEndpointSliceWithNewControllerEndpointSlice() {
		log.info("controller endpointslice changed, starting listeners update");
		endpointSliceRepository
				.findAllWithLabels(Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))
				.forEach(appEndpointSlice -> hijackIfNecessary(appEndpointSlice));
		log.info("controller endpointslice changed, end of listeners update");
	}
}
