package io.neo9.scaler.access.controllers.kubernetes;

import java.util.function.BiFunction;

import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.neo9.scaler.access.services.EndpointSliceHijackerService;
import io.neo9.scaler.access.utils.retry.RetryContext;
import io.neo9.scaler.access.utils.retry.RetryableWatcher;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.config.Labels.IS_SCALER_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_SCALER_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static java.util.Objects.nonNull;

@Component
@Slf4j
public class EndpointSliceController implements ReconnectableWatcher {

	private final KubernetesClient kubernetesClient;

	private final RetryContext retryContext = new RetryContext();

	private final BiFunction<Action, EndpointSlice, Void> onScalableEndpointSliceEventReceived;

	private final BiFunction<Action, EndpointSlice, Void> onControllerEndpointSliceEventReceived;

	private Watch endpointSliceWatchOnLabel;

	private Watch controllerEndpointSliceWatchOnLabel;

	public EndpointSliceController(KubernetesClient kubernetesClient, EndpointSliceHijackerService endpointSliceHijackerService) {
		this.kubernetesClient = kubernetesClient;
		this.onScalableEndpointSliceEventReceived = (action, endpointSlice) -> {
			String endpointNamespaceAndName = getResourceNamespaceAndName(endpointSlice);
			log.trace("start process event on {}", endpointNamespaceAndName);
			switch (action) {
				case ADDED:
				case MODIFIED:
					log.info("update event detected for endpointSlice : {}", endpointNamespaceAndName);
					endpointSliceHijackerService.hijack(endpointSlice);
					break;
				default:
					// do nothing on deletion
					break;
			}
			log.trace("end of process event on {}", endpointNamespaceAndName);
			return null;
		};

		this.onControllerEndpointSliceEventReceived = (action, endpointSlice) -> {
			String endpointNamespaceAndName = getResourceNamespaceAndName(endpointSlice);
			log.trace("start process event on {}", endpointNamespaceAndName);
			switch (action) {
				case ADDED:
				case MODIFIED:
					log.info("update event detected for endpointSlice : {}", endpointNamespaceAndName);
					endpointSliceHijackerService.reconcileEndpointSliceWithNewControllerEndpointSlice();
				default:
					// do nothing on deletion
					break;
			}
			log.trace("end of process event on {}", endpointNamespaceAndName);
			return null;
		};
	}

	public void startWatch(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		watchEndpointSliceOnLabel(reconnectableControllerOrchestrator);
		watchControllerEndpointSliceOnLabel(reconnectableControllerOrchestrator);
	}

	public void stopWatch() {
		closeEndpointSliceWatchOnLabel();
		closeControllerEndpointSliceWatchOnLabel();
		retryContext.shutdown();
	}

	private void closeEndpointSliceWatchOnLabel() {
		if (nonNull(endpointSliceWatchOnLabel)) {
			log.info("closing watch loop on endpoints (by label)");
			endpointSliceWatchOnLabel.close();
			endpointSliceWatchOnLabel = null;
		}
	}

	private void closeControllerEndpointSliceWatchOnLabel() {
		if (nonNull(controllerEndpointSliceWatchOnLabel)) {
			log.info("closing watch loop on controller endpointSlice (by label)");
			controllerEndpointSliceWatchOnLabel.close();
			controllerEndpointSliceWatchOnLabel = null;
		}
	}

	private void watchEndpointSliceOnLabel(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		closeEndpointSliceWatchOnLabel();
		log.info("starting watch loop on endpoint slice (by label)");
		endpointSliceWatchOnLabel = kubernetesClient.discovery().v1beta1().endpointSlices()
				.inAnyNamespace()
				.withLabel(IS_ALLOWED_TO_SCALE_LABEL_KEY, IS_ALLOWED_TO_SCALE_LABEL_VALUE)
				.watch(new RetryableWatcher<>(
						retryContext,
						String.format("%s-onLabel", EndpointSlice.class.getSimpleName()),
						reconnectableControllerOrchestrator::startOrRestartWatch,
						endpoint -> true,
						onScalableEndpointSliceEventReceived
				));
	}

	private void watchControllerEndpointSliceOnLabel(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		closeControllerEndpointSliceWatchOnLabel();
		log.info("starting watch loop on controller endpointSlice (by label)");
		controllerEndpointSliceWatchOnLabel = kubernetesClient.discovery().v1beta1().endpointSlices()
				.inAnyNamespace()
				.withLabel(IS_SCALER_LABEL_KEY, IS_SCALER_LABEL_VALUE)
				.watch(new RetryableWatcher<>(
						retryContext,
						String.format("%s-onControllerLabel", EndpointSlice.class.getSimpleName()),
						reconnectableControllerOrchestrator::startOrRestartWatch,
						endpoint -> true,
						onControllerEndpointSliceEventReceived
				));
	}

}
