package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.config.Annotations;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.NotHandledWorkloadException;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import io.neo9.scaler.access.utils.common.KubernetesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE;
import static io.neo9.scaler.access.config.Labels.ENDPOINT_SLICE_MANAGED_BY_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getAnnotationValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getWorkloadIdentifierLabels;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Service
@Slf4j
public class WorkloadService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	private final PodService podService;

	private final EndpointSliceRepository endpointSliceRepository;

	public WorkloadService(ScaleToZeroConfig scaleToZeroConfig, DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, PodService podService, EndpointSliceRepository endpointSliceRepository) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
		this.podService = podService;
		this.endpointSliceRepository = endpointSliceRepository;
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

	public HasMetadata annotate(HasMetadata workload, Map<String, String> annotations) {
		if (workload.getKind().equals(new Deployment().getKind())) {
			Deployment deployment = (Deployment) workload;
			return deploymentRepository.addToAnnotations(deployment, annotations);
		}
		else if (workload.getKind().equals(new StatefulSet().getKind())) {
			StatefulSet statefulSet = (StatefulSet) workload;
			return statefulsetRepository.addToAnnotations(statefulSet, annotations);
		}
		throw new NotHandledWorkloadException(String.format("could not identify workload to annotate : %s", getResourceNamespaceAndName(workload)));
	}

	public HasMetadata unannotated(HasMetadata workload, List<String> annotations) {
		if (workload.getKind().equals(new Deployment().getKind())) {
			Deployment deployment = (Deployment) workload;
			return deploymentRepository.removeFromAnnotations(deployment, annotations);
		}
		else if (workload.getKind().equals(new StatefulSet().getKind())) {
			StatefulSet statefulSet = (StatefulSet) workload;
			return statefulsetRepository.removeFromAnnotations(statefulSet, annotations);
		}
		throw new NotHandledWorkloadException(String.format("could not identify workload to unannotated : %s", getResourceNamespaceAndName(workload)));
	}

	public Integer getReplicaCount(HasMetadata workload) {
		if (workload.getKind().equals(new Deployment().getKind())) {
			Deployment deployment = (Deployment) workload;
			return deployment.getSpec().getReplicas();
		}
		else if (workload.getKind().equals(new StatefulSet().getKind())) {
			StatefulSet statefulSet = (StatefulSet) workload;
			return statefulSet.getSpec().getReplicas();
		}
		throw new NotHandledWorkloadException(String.format("could not identify workload to get replica count : %s", getResourceNamespaceAndName(workload)));
	}

	public Integer getOriginalReplicaCount(HasMetadata workload) {
		String controllerOriginalReplica = getAnnotationValue(Annotations.ORIGINAL_REPLICA, workload);
		if (isNotBlank(controllerOriginalReplica)) {
			return Integer.parseInt(controllerOriginalReplica);
		}

		for (String candidateAnnotation : scaleToZeroConfig.getOnUpscaleFallbackOriginalReplicasAnnotations()) {
			String candidateValue = getAnnotationValue(candidateAnnotation, workload);
			if (isNotBlank(candidateValue)) {
				return Integer.parseInt(candidateValue);
			}
		}

		return scaleToZeroConfig.getDefaultOnUpscaleReplicaCount();
	}

	public HasMetadata scale(HasMetadata workloadToScale, int replicaCount, boolean wait) {
		if (workloadToScale.getKind().equals(new Deployment().getKind())) {
			Deployment deployment = (Deployment) workloadToScale;
			return deploymentRepository.scale(deployment, replicaCount, wait);
		}
		else if (workloadToScale.getKind().equals(new StatefulSet().getKind())) {
			StatefulSet statefulSet = (StatefulSet) workloadToScale;
			return statefulsetRepository.scale(statefulSet, replicaCount, wait);
		}
		throw new NotHandledWorkloadException(String.format("could not identify workload to scale down : %s", getResourceNamespaceAndName(workloadToScale)));
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

	public boolean isHijacked(HasMetadata hasMetadata) {
		Map<String, String> appIdentifierLabels = getWorkloadIdentifierLabels(hasMetadata, scaleToZeroConfig.getApplicationIdentifierLabels());
		List<EndpointSlice> endpointSlicesOfDeployment = endpointSliceRepository.findAllWithLabels(hasMetadata.getMetadata().getNamespace(), appIdentifierLabels);
		log.info("endpoints : {}", endpointSlicesOfDeployment);
		return endpointSlicesOfDeployment.stream().anyMatch(appEndpointSlice -> ENDPOINT_SLICE_MANAGED_BY_CUSTOM_CONTROLLER_VALUE.equals(getLabelValue(ENDPOINT_SLICE_MANAGED_BY_KEY, appEndpointSlice)));
	}
}
