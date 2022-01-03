package io.neo9.scaler.access.controllers.kubernetes;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.services.StatefulsetHijackingService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Component
@Slf4j
public class StatefulsetController extends ReconnectableSingleWatcher<StatefulSet, StatefulSetList> {

	public StatefulsetController(KubernetesClient kubernetesClient, StatefulsetHijackingService statefulsetHijackingService) {
		super(
				/* unique name */
				"statefulset-onScalableLabel",
				/* watch what */
				kubernetesClient.apps().statefulSets()
						.inAnyNamespace()
						.withLabel(IS_ALLOWED_TO_SCALE_LABEL_KEY, IS_ALLOWED_TO_SCALE_LABEL_VALUE),
				/* on event */
				(action, statefulset) -> {
					String statefulsetNamespaceAndName = getResourceNamespaceAndName(statefulset);
					log.trace("start process event on {}", statefulsetNamespaceAndName);
					switch (action) {
						case ADDED:
						case MODIFIED:
							log.info("update event detected for : {}", statefulsetNamespaceAndName);
							try {
								statefulsetHijackingService.releaseIfNecessary(statefulset);
							}
							catch (MissingLabelException e) {
								log.error("panic: could not update statefulset", e);
							}
							break;
						default:
							// do nothing on deletion
							break;
					}
					log.trace("end of process event on {}", statefulsetNamespaceAndName);
					return null;
				}
		);
	}
}
