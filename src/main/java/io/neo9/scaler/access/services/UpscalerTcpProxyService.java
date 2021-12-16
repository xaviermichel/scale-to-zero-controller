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
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import io.neo9.scaler.access.utils.network.TcpServerProxyHandler;
import io.neo9.scaler.access.utils.network.TcpTableParser;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class UpscalerTcpProxyService {

	private final PodRepository podRepository;

	private final ServiceRepository serviceRepository;

	private final DeploymentRepository deploymentRepository;

	private final EndpointSliceRepository endpointSliceRepository;

	private final EndpointSliceHijackerService endpointSliceHijackerService;

	public UpscalerTcpProxyService(PodRepository podRepository, ServiceRepository serviceRepository, DeploymentRepository deploymentRepository, EndpointSliceRepository endpointSliceRepository, EndpointSliceHijackerService endpointSliceHijackerService) {
		this.podRepository = podRepository;
		this.serviceRepository = serviceRepository;
		this.deploymentRepository = deploymentRepository;
		this.endpointSliceRepository = endpointSliceRepository;
		this.endpointSliceHijackerService = endpointSliceHijackerService;
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

		boolean sourcePodHasIstio = sourcePod.getSpec().getContainers().stream().anyMatch(c -> c.getName().equals("istio-proxy"));
		//boolean sourcePodHasIstio = true; // TODO: check if istio is required
		String containerNameForTcpTable = sourcePodHasIstio ? "istio-proxy" : sourcePod.getSpec().getContainers().get(0).getName();

		String tcpTableRawOutput = podRepository.exec(sourcePod, containerNameForTcpTable, "cat", "/proc/net/tcp");
		List<io.fabric8.kubernetes.api.model.Service> targetedServices = TcpTableParser.parseTCPTable(tcpTableRawOutput).stream()
				.filter(t -> t.getState().equals("1"))   // TCP_ESTABLISHED
				.filter(t -> !t.getUid().equals("1337")) // from our app, not from envoy !
				.map(t -> t.getRemoteAddress())
				.map(ip -> serviceRepository.findServiceByIp(ip))
				.filter(Optional::isPresent)
				.map(opt -> opt.get())
				.filter(service -> IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, service)))
				.collect(Collectors.toList());

		for (io.fabric8.kubernetes.api.model.Service service : targetedServices) {
			String serviceNamespaceAndName = getResourceNamespaceAndName(service);
			log.debug("checking if workload behind service {} [{}] have to upscale", serviceNamespaceAndName, service.getSpec().getClusterIP());

			Set<String> serviceToDeploymentMatchingLabels = Set.of("app.kubernetes.io/name", "app.kubernetes.io/instance"); // TODO : pass as constant
			Map<String, String> appIdentifierLabels = new HashMap<>();
			for (String serviceToDeploymentMatchingLabel : serviceToDeploymentMatchingLabels) {
				String serviceLabelValue = getLabelValue(serviceToDeploymentMatchingLabel, service);
				if (StringUtils.isEmpty(serviceLabelValue)) {
					log.warn("missing label on service {} : {}", serviceNamespaceAndName, serviceToDeploymentMatchingLabel);
				}
				appIdentifierLabels.put(serviceToDeploymentMatchingLabel, serviceLabelValue);
			}

			Optional<Deployment> deploymentAttachedToService = deploymentRepository.findOneByLabels(service.getMetadata().getNamespace(), appIdentifierLabels);
			if (deploymentAttachedToService.isEmpty()) {
				log.warn("the forwarder proxy could not identify the deployment attached to the service : {}, dropping request", serviceNamespaceAndName);
				ctx.pipeline().close();
				return;
			}

			Deployment deployment = deploymentAttachedToService.get();
			String deploymentNamespaceAndName = getResourceNamespaceAndName(deployment);
			log.debug("will see if the deployment {} needs to scale", deploymentNamespaceAndName);

			if (!IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, deployment))) {
				log.warn("the deployment {}, does not have scaling label, won't do any action on it", deploymentNamespaceAndName);
				ctx.pipeline().close();
				return;
			}

			// wait for replica
			if (deployment.getSpec().getReplicas() == 0) {
				// TODO : not always scale to 1
				deploymentRepository.scale(deployment, 1, true);
			}
			log.debug("Deployment {} have at least one available replica", deploymentNamespaceAndName);

			// release endpoint slice
			EndpointSlice endpointSlice = endpointSliceRepository.findOneByLabels(service.getMetadata().getNamespace(), appIdentifierLabels).get();
			endpointSliceHijackerService.releaseHijacked(endpointSlice);

			// balance on 1st pod
			Pod targetPod = podRepository.findAllWithLabels(service.getMetadata().getNamespace(), appIdentifierLabels).get(0);
			targetPod = podRepository.waitUntilPodIsReady(targetPod, 60);

			InetSocketAddress clientRecipient = new InetSocketAddress(targetPod.getStatus().getPodIP(), ctx.remoteAddress().getPort());
			ctx.pipeline().addLast("ssTcpProxy", new TcpServerProxyHandler(clientRecipient));
		}

	}
}
