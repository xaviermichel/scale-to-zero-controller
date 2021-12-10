package io.neo9.scaler.access.controllers.kubernetes;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.neo9.scaler.access.services.EndpointHijackerService;
import io.neo9.scaler.access.utils.rate.Debouncer;
import io.neo9.scaler.access.utils.retry.RetryContext;
import io.neo9.scaler.access.utils.retry.RetryableWatcher;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static java.util.Objects.nonNull;

@Component
@Slf4j
public class EndpointsController implements ReconnectableWatcher {

	private final KubernetesClient kubernetesClient;

	private final RetryContext retryContext = new RetryContext();

	private final BiFunction<Action, Endpoints, Void> onEventReceived;

	private Watch endpointsWatchOnLabel;

	private final EndpointHijackerService endpointHijackerService;

	private final Debouncer debouncer = new Debouncer();

	public EndpointsController(KubernetesClient kubernetesClient, EndpointHijackerService endpointHijackerService) {
		this.kubernetesClient = kubernetesClient;
		this.endpointHijackerService = endpointHijackerService;
		this.onEventReceived = (action, endpoint) -> {
			String endpointNamespaceAndName = getResourceNamespaceAndName(endpoint);
			log.trace("start process event on {}", endpointNamespaceAndName);
			switch (action) {
				case ADDED:
				case MODIFIED:
					log.info("update event detected for endpoint : {}", endpointNamespaceAndName);
					endpointHijackerService.hijack(endpoint);
					break;
				default:
					// do nothing on ingress deletion
					break;
			}
			log.trace("end of process event on {}", endpointNamespaceAndName);
			return null;
		};
	}

	public void startWatch(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		watchEndpointsOnLabel(reconnectableControllerOrchestrator);
	}

	public void stopWatch() {
		closeEndpointsWatchOnLabel();
		retryContext.shutdown();
	}

	private void closeEndpointsWatchOnLabel() {
		if (nonNull(endpointsWatchOnLabel)) {
			log.info("closing watch loop on endpoints (by label)");
			endpointsWatchOnLabel.close();
			endpointsWatchOnLabel = null;
		}
	}

	private void watchEndpointsOnLabel(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		closeEndpointsWatchOnLabel();
		log.info("starting watch loop on endpoint (by label)");
		endpointsWatchOnLabel = kubernetesClient.endpoints()
				.inAnyNamespace()
				.withLabel(IS_ALLOWED_TO_SCALE_LABEL_KEY, IS_ALLOWED_TO_SCALE_LABEL_VALUE)
				.watch(new RetryableWatcher<>(
						retryContext,
						String.format("%s-onLabel", Endpoints.class.getSimpleName()),
						reconnectableControllerOrchestrator::startOrRestartWatch,
						endpoint -> true,
						onEventReceived
				));
	}

}
