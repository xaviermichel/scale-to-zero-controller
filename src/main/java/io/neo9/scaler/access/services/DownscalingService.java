package io.neo9.scaler.access.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.exceptions.InterruptedDownscaleException;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import io.neo9.scaler.access.utils.common.KubernetesUtils;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Annotations.ALL_DOWNSCALE_STRATEGY;
import static io.neo9.scaler.access.config.Annotations.DOWNSCALE_LOG_STRATEGY;
import static io.neo9.scaler.access.config.Annotations.DOWNSCALE_LOG_STRATEGY_EXCLUDE_PATTERN;
import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getAnnotationValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.haveAnyAnnotation;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@Slf4j
public class DownscalingService {

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	private final PodService podService;

	private final WorkloadService workloadService;

	public DownscalingService(DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, PodService podService, WorkloadService workloadService) {
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
		this.podService = podService;
		this.workloadService = workloadService;
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
	public void scaleDowLoop() {
		log.info("scale down loop");

		List<HasMetadata> workloads = new ArrayList<>();

		for (Deployment deployment : deploymentRepository.findAllInAnyNamespace(Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))) {
			if (haveAnyAnnotation(deployment, ALL_DOWNSCALE_STRATEGY) && workloadService.isStarted(deployment)) {
				workloads.add(deployment);
			}
		}
		for (StatefulSet statefulSet : statefulsetRepository.findAllInAnyNamespace(Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))) {
			if (haveAnyAnnotation(statefulSet, ALL_DOWNSCALE_STRATEGY) && workloadService.isStarted(statefulSet)) {
				workloads.add(statefulSet);
			}
		}

		log.info("will explore shut down possibilities for {}", workloads.stream().map(KubernetesUtils::getResourceNamespaceAndName).collect(toList()));
		for (HasMetadata workload : workloads) {
			if (isNotEmpty(getAnnotationValue(DOWNSCALE_LOG_STRATEGY, workload))) {
				scaleDownOnLogActivityStrategy(workload);
			}
			// more strategies may come here
		}
	}

	public void scaleDownOnLogActivityStrategy(HasMetadata workload) {
		Integer downScaleLogTimeoutInMinutes = Integer.parseInt(getAnnotationValue(DOWNSCALE_LOG_STRATEGY, workload).trim());
		List<Pod> pods = podService.listPodsAssociatedToWorkload(workload);
		boolean shouldDownscale = true;
		for (Pod pod : pods) {
			String activityLog = podService.getLogsSince(pod, downScaleLogTimeoutInMinutes, getAnnotationValue(DOWNSCALE_LOG_STRATEGY_EXCLUDE_PATTERN, workload));
			if (isNotEmpty(activityLog)) {
				shouldDownscale = false;
				break;
			}
		}

		if (shouldDownscale) {
			log.info("downscaling {}", getResourceNamespaceAndName(workload));
			scaleDown(workload);
		}
	}

	public void scaleDown(HasMetadata workloadToScale) {
		if (workloadToScale.getKind().equals(new Deployment().getKind())) {
			Deployment deployment = (Deployment) workloadToScale;
			if (deployment.getSpec().getReplicas() == 0) {
				deploymentRepository.scale(deployment, 0, true);
			}
		}
		else if (workloadToScale.getKind().equals(new StatefulSet().getKind())) {
			StatefulSet statefulSet = (StatefulSet) workloadToScale;
			if (statefulSet.getSpec().getReplicas() == 0) {
				statefulsetRepository.scale(statefulSet, 0, true);
			}
		}
		else {
			throw new InterruptedDownscaleException(String.format("could not identify workload to scale down : %s", workloadToScale));
		}
	}

}
