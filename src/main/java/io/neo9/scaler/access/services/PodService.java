package io.neo9.scaler.access.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.repositories.PodRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
@Slf4j
public class PodService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final PodRepository podRepository;

	public PodService(ScaleToZeroConfig scaleToZeroConfig, PodRepository podRepository) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.podRepository = podRepository;
	}

	public Pod getSourcePod(String sourcePodIp) {
		Optional<Pod> podByIp = podRepository.findPodByIp(sourcePodIp);
		return podByIp.orElseThrow(
				() -> new InterruptedProxyForwardException(String.format("the forwarder proxy received a request from an host (%s) that it couldn't identify, dropping request", sourcePodIp))
		);
	}

	public Pod waitForMatchingPodInReadyState(String namespace, Map<String, String> applicationsIdentifierLabels, int timeoutInSeconds, boolean waitForAll) {
		List<Pod> targetPods = podRepository.findAllWithLabels(namespace, applicationsIdentifierLabels)
				.stream().filter(pod -> isEmpty(pod.getMetadata().getDeletionTimestamp()))
				.collect(Collectors.toList());
		if (targetPods.isEmpty()) {
			throw new InterruptedProxyForwardException(String.format("did not find the pods in namespace %s, with labels %s, can't forward request", namespace, applicationsIdentifierLabels));
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

}
