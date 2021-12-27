package io.neo9.scaler.access.services;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import io.neo9.scaler.access.utils.network.TcpTableEntry;
import io.neo9.scaler.access.utils.network.TcpTableParser;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
@Slf4j
public class UpscalerTcpProxyService {

	private final PodRepository podRepository;

	private final ServiceRepository serviceRepository;

	private final DeploymentRepository deploymentRepository;

	private final EndpointSliceRepository endpointSliceRepository;

	private final EndpointSliceHijackerService endpointSliceHijackerService;

	private final ScaleToZeroConfig scaleToZeroConfig;

	public UpscalerTcpProxyService(PodRepository podRepository, ServiceRepository serviceRepository, DeploymentRepository deploymentRepository, EndpointSliceRepository endpointSliceRepository, EndpointSliceHijackerService endpointSliceHijackerService, ScaleToZeroConfig scaleToZeroConfig) {
		this.podRepository = podRepository;
		this.serviceRepository = serviceRepository;
		this.deploymentRepository = deploymentRepository;
		this.endpointSliceRepository = endpointSliceRepository;
		this.endpointSliceHijackerService = endpointSliceHijackerService;
		this.scaleToZeroConfig = scaleToZeroConfig;
	}

	/**
	 * Takes source and destination, and return the endpoint to add to proxy chain
	 */
	public InetSocketAddress forwardRequest(InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
		Pod sourcePod = getSourcePod(remoteAddress.getAddress().getHostAddress());
		log.info("forwarding request from {}", sourcePod.getMetadata().getName());

		io.fabric8.kubernetes.api.model.Service targetedService = getTargetedScalingServiceByPod(sourcePod, localAddress);
		String serviceNamespaceAndName = getResourceNamespaceAndName(targetedService);
		log.debug("checking if workload behind {} [{}] have to upscale", serviceNamespaceAndName, targetedService.getSpec().getClusterIP());

		Map<String, String> applicationsIdentifierLabels = getApplicationsIdentifierLabels(targetedService);

		Deployment deployment = deploymentRepository.findOneByLabels(targetedService.getMetadata().getNamespace(), applicationsIdentifierLabels)
				.orElseThrow(() -> new InterruptedProxyForwardException(String.format("the forwarder proxy could not identify the deployment attached to %s, dropping request", serviceNamespaceAndName)));
		String deploymentNamespaceAndName = getResourceNamespaceAndName(deployment);

		log.debug("checking if {} needs to scale", deploymentNamespaceAndName);
		if (!IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, deployment))) {
			throw new InterruptedProxyForwardException(String.format("%s, does not have scaling label, won't do any action on it", deploymentNamespaceAndName));
		}

		// scale up
		if (deployment.getSpec().getReplicas() == 0) {
			deploymentRepository.scale(deployment, 2, true); // TODO : not always scale to 2
		}
		log.debug("{} have at least one replica", deploymentNamespaceAndName);

		// release endpoint slice
		endpointSliceHijackerService.releaseHijacked(targetedService.getMetadata().getNamespace(), applicationsIdentifierLabels);

		// balance on 1st pod
		Pod targetPod = waitForMatchingPodInReadyState(targetedService.getMetadata().getNamespace(), applicationsIdentifierLabels, 60);

		return new InetSocketAddress(targetPod.getStatus().getPodIP(), localAddress.getPort());
	}

	private Pod getSourcePod(String sourcePodIp) {
		Optional<Pod> podByIp = podRepository.findPodByIp(sourcePodIp);
		return podByIp.orElseThrow(
				() -> new InterruptedProxyForwardException(String.format("the forwarder proxy received a request from an host (%s) that it couldn't identify, dropping request", sourcePodIp))
		);
	}

	private io.fabric8.kubernetes.api.model.Service getTargetedScalingServiceByPod(Pod sourcePod, InetSocketAddress localAddress) {
		boolean sourcePodHasIstio = sourcePod.getSpec().getContainers().stream().anyMatch(c -> c.getName().equals("istio-proxy"));
		//boolean sourcePodHasIstio = true; // TODO: check if istio is required
		String containerNameForTcpTable = sourcePodHasIstio ? "istio-proxy" : sourcePod.getSpec().getContainers().get(0).getName();

		String tcpTableRawOutput = podRepository.exec(sourcePod, containerNameForTcpTable, "cat", "/proc/net/tcp");
		List<TcpTableEntry> tcpTableEntries = TcpTableParser.parseTCPTable(tcpTableRawOutput);
		log.trace("decoded entries : {}", tcpTableEntries);

		Optional<io.fabric8.kubernetes.api.model.Service> targetedServiceOpt = tcpTableEntries.stream()
				// filter non desired entries
				.filter(t -> t.getState().equals("1"))   // TCP_ESTABLISHED
				.filter(t -> !t.getUid().equals("1337")) // from our app, not from envoy !
				// more filtering : which may target our service
				.filter(t -> t.getRemotePort().equals(String.valueOf(localAddress.getPort())))
				// order sources, last (newest) calls firsts
				.map(t -> t.getRemoteAddress())
				.sorted(Collections.reverseOrder())
				.distinct()
				// get source service
				.map(ip -> serviceRepository.findServiceByIp(ip))
				.filter(Optional::isPresent)
				.map(opt -> opt.get())
				.filter(service -> IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, service)))
				.findFirst();

		return targetedServiceOpt.orElseThrow(
				() -> new InterruptedProxyForwardException("could not detected the service at the source of the request, aborting")
		);
	}

	private Pod waitForMatchingPodInReadyState(String namespace, Map<String, String> applicationsIdentifierLabels, int timeoutInSeconds) {
		List<Pod> targetPods = podRepository.findAllWithLabels(namespace, applicationsIdentifierLabels);
		if (targetPods.isEmpty()) {
			throw new InterruptedProxyForwardException(String.format("did not find the pods in namespace %s, with labels %s, can't forward request", namespace, applicationsIdentifierLabels));
		}

		Pod targetPod = targetPods.get(0);
		String targetPodNameAndNamespace = getResourceNamespaceAndName(targetPod);
		log.debug("waiting for pod {} to be READY", targetPodNameAndNamespace);
		targetPod = podRepository.waitUntilPodIsReady(targetPod, timeoutInSeconds);
		log.debug("pod {} is ready", targetPodNameAndNamespace);
		return targetPod;
	}

	private Map<String, String> getApplicationsIdentifierLabels(HasMetadata hasMetadata) {
		Map<String, String> appIdentifierLabels = new HashMap<>();
		for (String applicationIdentifierLabel : scaleToZeroConfig.getApplicationIdentifierLabels()) {
			String sourceLabelValue = getLabelValue(applicationIdentifierLabel, hasMetadata);
			if (isEmpty(sourceLabelValue)) {
				throw new InterruptedProxyForwardException(String.format("missing app identifier label on source %s : %s, aborting", getResourceNamespaceAndName(hasMetadata), applicationIdentifierLabel));
			}
			appIdentifierLabels.put(applicationIdentifierLabel, sourceLabelValue);
		}
		return appIdentifierLabels;
	}

}
