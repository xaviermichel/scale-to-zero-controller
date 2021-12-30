package io.neo9.scaler.access.repositories;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static org.apache.commons.lang3.StringUtils.EMPTY;

@Component
@Slf4j
public class PodRepository {

	private final KubernetesClient kubernetesClient;

	public PodRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	public Optional<Pod> findPodByIp(String ipAddress) {
		return kubernetesClient.pods()
				.inAnyNamespace()
				.list().getItems().stream()
				.filter(pod -> ipAddress.equals(pod.getStatus().getPodIP()))
				.findFirst();
	}

	public List<Pod> findAllWithLabels(String namespace, Map<String, String> filteringLabels) {
		return kubernetesClient.pods()
				.inNamespace(namespace)
				.withLabels(filteringLabels)
				.list().getItems();
	}

	public List<Pod> findAllWithLabelsInPhase(String namespace, Map<String, String> filteringLabels, String phase) {
		return kubernetesClient.pods()
				.inNamespace(namespace)
				.withLabels(filteringLabels)
				.withField("status.phase", phase)
				.list().getItems();
	}


	public Pod waitUntilPodIsReady(Pod pod, int timeoutInSeconds) {
		return kubernetesClient.resource(pod)
				.inNamespace(pod.getMetadata().getNamespace())
				.waitUntilReady(timeoutInSeconds, TimeUnit.SECONDS);
	}

	public String readFileFromPod(Pod pod, String containerId, String path) {
		try (InputStream is = kubernetesClient.pods()
				.inNamespace(pod.getMetadata().getNamespace())
				.withName(pod.getMetadata().getName())
				.inContainer(containerId)
				.file(path).read()) {
			return new BufferedReader(new InputStreamReader(is)).lines().collect(Collectors.joining("\n"));
		}
		catch (IOException e) {
			log.error("Failed to read file {} in {}/{}", path, getResourceNamespaceAndName(pod), containerId);
		}
		return EMPTY;
	}
}
