package io.neo9.scaler.access.controllers.kubernetes;

import java.util.function.BiFunction;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watch;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.neo9.scaler.access.utils.retry.RetryContext;
import io.neo9.scaler.access.utils.retry.RetryableWatcher;
import lombok.extern.slf4j.Slf4j;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static java.util.Objects.nonNull;

//@Component
@Slf4j
public class EndpointsController implements ReconnectableWatcher {

	private final KubernetesClient kubernetesClient;

	private final RetryContext retryContext = new RetryContext();

	private final BiFunction<Action, Endpoints, Void> onEventReceived;

	private Watch serviceWatchOnLabel;

	public EndpointsController(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
		this.onEventReceived = (action, service) -> {
			String serviceNamespaceAndName = getResourceNamespaceAndName(service);
			log.trace("start process event on {}", serviceNamespaceAndName);
			log.trace("end of process event on {}", serviceNamespaceAndName);
			return null;
		};
	}

	public void startWatch(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		watchServicesOnLabel(reconnectableControllerOrchestrator);
	}

	public void stopWatch() {
		closeServicesWatchOnLabel();
		retryContext.shutdown();
	}

	private void closeServicesWatchOnLabel() {
		if (nonNull(serviceWatchOnLabel)) {
			log.info("closing watch loop on service (by label)");
			serviceWatchOnLabel.close();
			serviceWatchOnLabel = null;
		}
	}

	private void watchServicesOnLabel(ReconnectableControllerOrchestrator reconnectableControllerOrchestrator) {
		closeServicesWatchOnLabel();
		log.info("starting watch loop on service (by label)");
		serviceWatchOnLabel = kubernetesClient.endpoints()
				.inAnyNamespace()
				//.withLabel(EXPOSE_LABEL_KEY, EXPOSE_LABEL_VALUE)
				.watch(new RetryableWatcher<>(
						retryContext,
						String.format("%s-onLabel", Endpoints.class.getSimpleName()),
						reconnectableControllerOrchestrator::startOrRestartWatch,
						service -> true,
						onEventReceived
				));
	}

}
