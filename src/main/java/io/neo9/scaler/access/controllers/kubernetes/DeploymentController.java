package io.neo9.scaler.access.controllers.kubernetes;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.services.WorkloadHijackingService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Component
@Slf4j
public class DeploymentController extends ReconnectableSingleWatcher<Deployment, DeploymentList> {

	public DeploymentController(KubernetesClient kubernetesClient, WorkloadHijackingService workloadHijackingService) {
		super(
				/* unique name */
				"deployment-onScalableLabel",
				/* watch what */
				kubernetesClient.apps().deployments()
						.inAnyNamespace()
						.withLabel(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE),
				/* on event */
				(action, deployment) -> {
					String deploymentNamespaceAndName = getResourceNamespaceAndName(deployment);
					log.trace("start process event on {}", deploymentNamespaceAndName);
					switch (action) {
						case ADDED:
						case MODIFIED:
							log.info("update event detected for : {}", deploymentNamespaceAndName);
							try {
								workloadHijackingService.releaseIfNecessary(deployment);
							}
							catch (MissingLabelException e) {
								log.error("panic: could not update deployment", e);
							}
							break;
						default:
							// do nothing on deletion
							break;
					}
					log.trace("end of process event on {}", deploymentNamespaceAndName);
					return null;
				}
		);
	}
}
