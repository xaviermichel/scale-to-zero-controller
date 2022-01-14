package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.repositories.PodRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getWorkloadIdentifierLabels;
import static io.neo9.scaler.access.utils.common.StringUtils.NEW_LINE;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@Slf4j
public class PodService {

	private final PodRepository podRepository;

	private final ScaleToZeroConfig scaleToZeroConfig;

	public PodService(PodRepository podRepository, ScaleToZeroConfig scaleToZeroConfig) {
		this.podRepository = podRepository;
		this.scaleToZeroConfig = scaleToZeroConfig;
	}

	public Pod getSourcePod(String sourcePodIp) {
		Optional<Pod> podByIp = podRepository.findPodByIp(sourcePodIp);
		return podByIp.orElseThrow(
				() -> new InterruptedProxyForwardException(String.format("the forwarder proxy received a request from an host (%s) that it couldn't identify, dropping request", sourcePodIp))
		);
	}

	public List<Pod> listPodsAssociatedToWorkload(HasMetadata hasMetadata) {
		return podRepository.findAllWithLabels(hasMetadata.getMetadata().getNamespace(), getWorkloadIdentifierLabels(hasMetadata, scaleToZeroConfig.getApplicationIdentifierLabels()))
				.stream().filter(pod -> !isTerminating(pod))
				.collect(Collectors.toList());
	}

	public Pod waitForMatchingPodInReadyState(HasMetadata parentWorkload, int timeoutInSeconds, boolean waitForAll) {
		List<Pod> targetPods = listPodsAssociatedToWorkload(parentWorkload);
		if (targetPods.isEmpty()) {
			throw new InterruptedProxyForwardException(String.format("did not find the pods in namespace %s, with labels %s, can't forward request", parentWorkload.getMetadata().getNamespace(), scaleToZeroConfig.getApplicationIdentifierLabels()));
		}

		Pod futureTargetPod = null;
		for (Pod targetPod : targetPods) {
			String targetPodNameAndNamespace = getResourceNamespaceAndName(targetPod);
			log.debug("waiting for {} to be READY", targetPodNameAndNamespace);
			futureTargetPod = podRepository.waitUntilPodIsReady(targetPod, timeoutInSeconds);
			log.debug("{} is ready", targetPodNameAndNamespace);
			if (!waitForAll) {
				break;
			}
		}

		return futureTargetPod;
	}

	public boolean isStarted(Pod pod) {
		return pod.getStatus().getContainerStatuses().stream()
				.anyMatch(ContainerStatus::getStarted);
	}

	public boolean isReady(Pod pod) {
		return pod.getStatus().getContainerStatuses().stream()
				.allMatch(ContainerStatus::getReady);
	}

	public boolean isTerminating(Pod pod) {
		return isNotEmpty(pod.getMetadata().getDeletionTimestamp());
	}

	public String getLogsSince(Pod pod, int sinceMinutes, String exclusionRegex) {
		String completePodLog = podRepository.getLogsSince(pod, sinceMinutes);
		if (isEmpty(exclusionRegex)) {
			return completePodLog;
		}
		return completePodLog.lines().filter(line -> !Pattern.matches(exclusionRegex, line)).collect(Collectors.joining(NEW_LINE)) + NEW_LINE;
	}
}
