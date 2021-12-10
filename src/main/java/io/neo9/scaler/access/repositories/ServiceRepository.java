package io.neo9.scaler.access.repositories;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ServiceRepository {

	private final KubernetesClient kubernetesClient;

	public ServiceRepository(KubernetesClient kubernetesClient) {
		this.kubernetesClient = kubernetesClient;
	}

	public Service find(String namespace, String name) {
		return kubernetesClient.services()
				.inNamespace(namespace)
				.withName(name)
				.fromServer()
				.get();
	}

	public Optional<Service> findServiceByIp(String ipAddress) {
		return kubernetesClient.services().inAnyNamespace().list().getItems()
				.stream()
				.filter(svc -> ipAddress.equals(svc.getSpec().getClusterIP()))
				.findFirst();
	}

}
