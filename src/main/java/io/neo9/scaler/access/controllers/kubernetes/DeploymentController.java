package io.neo9.scaler.access.controllers.kubernetes;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.services.DeploymentHijackingService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Component
@Slf4j
public class DeploymentController extends ReconnectableSingleWatcher<Deployment, DeploymentList> {

	private final KubernetesClient kubernetesClient;

	public DeploymentController(KubernetesClient kubernetesClient, DeploymentHijackingService deploymentHijackingService) {
		super("deployment-onScalableLabel", (action, deployment) -> {
			String deploymentNamespaceAndName = getResourceNamespaceAndName(deployment);
			log.trace("start process event on {}", deploymentNamespaceAndName);
			switch (action) {
				case ADDED:
				case MODIFIED:
					log.info("update event detected for : {}", deploymentNamespaceAndName);
					try {
						deploymentHijackingService.releaseIfNecessary(deployment);
					}
					catch (MissingLabelException e) {
						log.error("panic: could not update endpoint", e);
					}
					break;
				default:
					// do nothing on deletion
					break;
			}
			log.trace("end of process event on {}", deploymentNamespaceAndName);
			return null;
		});
		this.kubernetesClient = kubernetesClient;
	}

	@Override
	public FilterWatchListDeletable<Deployment, DeploymentList> watch() {
		return kubernetesClient.apps().deployments()
				.inAnyNamespace()
				.withLabel(IS_ALLOWED_TO_SCALE_LABEL_KEY, IS_ALLOWED_TO_SCALE_LABEL_VALUE);
	}
}
