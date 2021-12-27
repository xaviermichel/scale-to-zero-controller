package io.neo9.scaler.access.repositories;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;

import org.springframework.stereotype.Component;

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

	public Pod waitUntilPodIsReady(Pod pod, int timeoutInSeconds) {
		return kubernetesClient.resource(pod)
				.inNamespace(pod.getMetadata().getNamespace())
				.waitUntilReady(timeoutInSeconds, TimeUnit.SECONDS);
	}

	public String exec(Pod pod, String containerId, String... command) {
		CountDownLatch execLatch = new CountDownLatch(1);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteArrayOutputStream error = new ByteArrayOutputStream();

		ExecWatch execWatch = kubernetesClient.pods()
				.inNamespace(pod.getMetadata().getNamespace()).withName(pod.getMetadata().getName())
				.inContainer(containerId)
				.writingOutput(out)
				.writingError(error)
				.usingListener(new ExecListener() {
					@Override
					public void onOpen(Response response) {
						log.trace("shell onOpen");
					}

					@Override
					public void onFailure(Throwable throwable, Response response) {
						log.warn("could not exec command on pod");
						execLatch.countDown();
					}

					@Override
					public void onClose(int i, String s) {
						log.trace("shell onClose");
						execLatch.countDown();
					}
				})
				.exec(command);

		boolean latchTerminationStatus = false;
		try {
			latchTerminationStatus = execLatch.await(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e) {
			log.error("Failed to exec command", e);
		}
		if (!latchTerminationStatus) {
			log.warn("execution timeout");
		}
		log.trace("exec output: {} ", out);
		execWatch.close();
		return out.toString();
	}
}
