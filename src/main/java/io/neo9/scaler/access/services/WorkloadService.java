package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WorkloadService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	private final PodService podService;

	public WorkloadService(ScaleToZeroConfig scaleToZeroConfig, DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, PodService podService) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
		this.podService = podService;
	}

	@Nullable
	public HasMetadata getWorkload(String namespace, Map<String, String> applicationsIdentifierLabels) {
		Optional<Deployment> deploymentOpt = deploymentRepository.findOneByLabels(namespace, applicationsIdentifierLabels);
		Optional<StatefulSet> statefulSetOpt = statefulsetRepository.findOneByLabels(namespace, applicationsIdentifierLabels);

		return ObjectUtils.firstNonNull(deploymentOpt.orElse(null), statefulSetOpt.orElse(null));
	}

	public HasMetadata getWorkload(String namespace, String workloadName) {
		Optional<Deployment> deploymentOpt = deploymentRepository.findOne(namespace, workloadName);
		Optional<StatefulSet> statefulSetOpt = statefulsetRepository.findOne(namespace, workloadName);

		return ObjectUtils.firstNonNull(deploymentOpt.orElse(null), statefulSetOpt.orElse(null));
	}

	/**
	 * wait for :
	 * * all pods in case of statefulset
	 * * one pod in case of deployment
	 */
	public Pod waitForWorkloadToBeReady(HasMetadata hasMetadata) {
		boolean isWorkloadStateful = hasMetadata.getKind().equals(new StatefulSet().getKind());
		return podService.waitForMatchingPodInReadyState(hasMetadata, 300, isWorkloadStateful);
	}

	public boolean isStarted(HasMetadata hasMetadata) {
		List<Pod> pods = podService.listPodsAssociatedToWorkload(hasMetadata);
		if (pods.isEmpty()) {
			return false;
		}

		return pods.stream().anyMatch(p -> podService.isStarted(p));
	}

	public boolean isReady(HasMetadata hasMetadata) {
		List<Pod> pods = podService.listPodsAssociatedToWorkload(hasMetadata);
		if (pods.isEmpty()) {
			return false;
		}

		// all pods in case of statefulset
		boolean isWorkloadStateful = hasMetadata.getKind().equals(new StatefulSet().getKind());
		if (isWorkloadStateful) {
			StatefulSet statefulSet = (StatefulSet) hasMetadata;
			if (!statefulSet.getSpec().getReplicas().equals(pods.size())) {
				return false;
			}
			return pods.stream().allMatch(p -> podService.isReady(p));
		}

		// at least one in other cases
		return pods.stream().anyMatch(p -> podService.isReady(p));
	}
}
