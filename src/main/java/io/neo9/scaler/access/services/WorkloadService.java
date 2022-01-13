package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelsValues;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
@Slf4j
public class WorkloadService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	private final PodService podService;

	private final PodRepository podRepository;

	public WorkloadService(ScaleToZeroConfig scaleToZeroConfig, DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, PodService podService, PodRepository podRepository) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
		this.podService = podService;
		this.podRepository = podRepository;
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

	public Map<String, String> getWorkloadIdentifierLabels(HasMetadata hasMetadata) {
		try {
			return getLabelsValues(hasMetadata, scaleToZeroConfig.getApplicationIdentifierLabels());
		}
		catch (MissingLabelException e) {
			throw new InterruptedProxyForwardException(String.format("missing app identifier label on source %s : %s, aborting", getResourceNamespaceAndName(hasMetadata), scaleToZeroConfig.getApplicationIdentifierLabels()), e);
		}
	}

	/**
	 * wait for :
	 * * all pods in case of statefulset
	 * * one pod in case of deployment
	 */
	public Pod waitForWorkloadToBeReady(HasMetadata hasMetadata) {
		boolean isWorkloadStateful = hasMetadata.getKind().equals(new StatefulSet().getKind());
		return podService.waitForMatchingPodInReadyState(hasMetadata.getMetadata().getNamespace(), getWorkloadIdentifierLabels(hasMetadata), 300, isWorkloadStateful);
	}

	public boolean isStarted(HasMetadata hasMetadata) {
		List<Pod> pods = podRepository.findAllWithLabels(hasMetadata.getMetadata().getNamespace(), getWorkloadIdentifierLabels(hasMetadata))
				.stream().filter(pod -> isEmpty(pod.getMetadata().getDeletionTimestamp()))
				.collect(Collectors.toList());

		if (pods.isEmpty()) {
			return false;
		}

		return pods.stream().anyMatch(p -> podService.isStarted(p));
	}

	public boolean isReady(HasMetadata hasMetadata) {
		List<Pod> pods = podRepository.findAllWithLabels(hasMetadata.getMetadata().getNamespace(), getWorkloadIdentifierLabels(hasMetadata))
				.stream().filter(pod -> isEmpty(pod.getMetadata().getDeletionTimestamp()))
				.collect(Collectors.toList());

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
