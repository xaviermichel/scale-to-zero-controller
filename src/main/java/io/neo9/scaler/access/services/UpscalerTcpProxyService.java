package io.neo9.scaler.access.services;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import io.neo9.scaler.access.utils.common.KubernetesUtils;
import io.neo9.scaler.access.utils.network.TcpServerProxyHandler;
import io.neo9.scaler.access.utils.network.TcpTableParser;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;

@Service
@Slf4j
public class UpscalerTcpProxyService {

	private final PodRepository podRepository;

	private final ServiceRepository serviceRepository;

	private final DeploymentRepository deploymentRepository;

	public UpscalerTcpProxyService(PodRepository podRepository, ServiceRepository serviceRepository, DeploymentRepository deploymentRepository) {
		this.podRepository = podRepository;
		this.serviceRepository = serviceRepository;
		this.deploymentRepository = deploymentRepository;
	}

	public void forwardRequest(NioSocketChannel ctx) {
		String sourcePodIp = ctx.remoteAddress().getAddress().getHostAddress();

		Optional<Pod> podByIp = podRepository.findPodByIp(sourcePodIp);
		if (podByIp.isEmpty()) {
			log.warn("the forwarder proxy received a request from an host ({}) that it couldn't identify, dropping request", sourcePodIp);
			ctx.pipeline().close();
			return;
		}

		Pod sourcePod = podByIp.get();
		log.info("forwarding request from {} [{}]", sourcePod.getMetadata().getName(), sourcePodIp);

		String tcpTableRawOutput = podRepository.exec(sourcePod, "istio-proxy", "cat", "/proc/net/tcp");
		List<io.fabric8.kubernetes.api.model.Service> targetedServices = TcpTableParser.parseTCPTable(tcpTableRawOutput).stream()
				.filter(t -> t.getState().equals("1"))   // TCP_ESTABLISHED
				.filter(t -> !t.getUid().equals("1337")) // from our app, not from envoy !
				.map(t -> t.getRemoteAddress())
				.map(ip -> serviceRepository.findServiceByIp(ip))
				.filter(Optional::isPresent)
				.map(opt -> opt.get())
				.collect(Collectors.toList());

		for (io.fabric8.kubernetes.api.model.Service service : targetedServices) {
			String serviceNamespaceAndName = KubernetesUtils.getResourceNamespaceAndName(service);
			log.debug("checking if workload behind service {} [{}] have to upscale", serviceNamespaceAndName, service.getSpec().getClusterIP());

			Set<String> serviceToDeploymentMatchingLabels = Set.of("app.kubernetes.io/name", "app.kubernetes.io/version", "app.kubernetes.io/instance");
			Map<String, String> serviceToDeploymentMatchLabels = new HashMap<>();
			for (String serviceToDeploymentMatchingLabel : serviceToDeploymentMatchingLabels) {
				String serviceLabelValue = getLabelValue(serviceToDeploymentMatchingLabel, service);
				serviceToDeploymentMatchLabels.put(serviceToDeploymentMatchingLabel, serviceLabelValue);
			}

			Optional<Deployment> deploymentAttachedToService = deploymentRepository.findByLabels(service.getMetadata().getNamespace(), serviceToDeploymentMatchLabels);
			if (deploymentAttachedToService.isEmpty()) {
				log.warn("the forwarder proxy could not identify the deployment attached to the service : {}, dropping request", serviceNamespaceAndName);
				ctx.pipeline().close();
				return;
			}

			Deployment deployment = deploymentAttachedToService.get();
			String deploymentNamespaceAndName = KubernetesUtils.getResourceNamespaceAndName(deployment);
			log.debug("will see if the deployment {} needs to scale", deploymentNamespaceAndName);

			if (! IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, deployment))) {
				log.warn("the deployment {}, does not have scaling label, won't do any action on it", deploymentNamespaceAndName);
				ctx.pipeline().close();
				return;
			}

			// wait for replica
			if (deployment.getStatus().getReadyReplicas() > 0) {
				// TODO : scale up
			}

			// TODO: add security target != self

//			InetSocketAddress clientRecipient = new InetSocketAddress(service.getSpec().getClusterIP(), ctx.remoteAddress().getPort());
			InetSocketAddress clientRecipient = new InetSocketAddress("34.102.134.230", 80);
			ctx.pipeline().addLast("ssTcpProxy", new TcpServerProxyHandler(clientRecipient));
		}

	}
}
