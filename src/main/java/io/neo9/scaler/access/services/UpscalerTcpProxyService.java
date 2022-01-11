package io.neo9.scaler.access.services;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import io.neo9.scaler.access.utils.network.TcpTableEntry;
import io.neo9.scaler.access.utils.network.TcpTableParser;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Annotations.BEFORE_SCALE_REQUIREMENTS;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_VALUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getAnnotationValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.StringUtils.COMMA;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Service
@Slf4j
public class UpscalerTcpProxyService {

	private static final String ISTIO_SIDECAR_CONTAINER_NAME = "istio-proxy";

	private final PodRepository podRepository;

	private final ServiceRepository serviceRepository;

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	private final WorkloadService workloadService;

	private final PodService podService;

	public UpscalerTcpProxyService(PodRepository podRepository, ServiceRepository serviceRepository, DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, WorkloadService workloadService, PodService podService) {
		this.podRepository = podRepository;
		this.serviceRepository = serviceRepository;
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
		this.workloadService = workloadService;
		this.podService = podService;
	}

	/**
	 * Takes source and destination, and return the endpoint to add to proxy chain
	 */
	public InetSocketAddress forwardRequest(InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
		Pod sourcePod = podService.getSourcePod(remoteAddress.getAddress().getHostAddress());
		log.info("forwarding request from {}", getResourceNamespaceAndName(sourcePod));

		io.fabric8.kubernetes.api.model.Service targetedService = getTargetedScalingServiceByPod(sourcePod, localAddress);
		String serviceNamespaceAndName = getResourceNamespaceAndName(targetedService);
		log.debug("checking if workload behind {} [{}] have to upscale", serviceNamespaceAndName, targetedService.getSpec().getClusterIP());

		Map<String, String> applicationsIdentifierLabels = workloadService.getWorkloadIdentifierLabels(targetedService);

		HasMetadata workloadToScale = workloadService.getWorkload(targetedService.getMetadata().getNamespace(), applicationsIdentifierLabels);
		if (workloadToScale == null) {
			throw new InterruptedProxyForwardException(String.format("the forwarder proxy could not identify the deployment attached to %s, dropping request", serviceNamespaceAndName));
		}

		String workloadNamespaceAndName = getResourceNamespaceAndName(workloadToScale);
		log.debug("checking if {} needs to scale", workloadNamespaceAndName);
		if (!IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, workloadToScale))) {
			throw new InterruptedProxyForwardException(String.format("%s, does not have scaling label, won't do any action on it", workloadToScale));
		}

		Pod targetPod = scaleUp(workloadToScale);
		log.debug("{} have at least one replica required", workloadNamespaceAndName);

		return new InetSocketAddress(targetPod.getStatus().getPodIP(), localAddress.getPort());
	}

	private Pod scaleUp(HasMetadata workloadToScale) {
		return scaleUp(workloadToScale, true);
	}

	private Pod scaleUp(HasMetadata workloadToScale, boolean waitForScaleFinished) {
		Map<String, String> applicationsIdentifierLabels = workloadService.getWorkloadIdentifierLabels(workloadToScale);
		handleBeforeScaleRequirements(workloadToScale);

		if (workloadToScale.getKind().equals(new Deployment().getKind())) {
			Deployment deployment = (Deployment) workloadToScale;
			if (deployment.getSpec().getReplicas() == 0) {
				deploymentRepository.scale(deployment, 2, true); // TODO : not always scale to 2
			}
		}
		else if (workloadToScale.getKind().equals(new StatefulSet().getKind())) {
			StatefulSet statefulSet = (StatefulSet) workloadToScale;
			if (statefulSet.getSpec().getReplicas() == 0) {
				statefulsetRepository.scale(statefulSet, 2, true); // TODO : not always scale to 2
			}
		}
		else {
			throw new InterruptedProxyForwardException(String.format("could not identify workload to scale up", workloadToScale));
		}

		if (!waitForScaleFinished) {
			return null;
		}

		// wait for all pods in case of stateful workload
		boolean isWorkloadStateful = workloadToScale.getKind().equals(new StatefulSet().getKind());
		return podService.waitForMatchingPodInReadyState(workloadToScale.getMetadata().getNamespace(), applicationsIdentifierLabels, 60, isWorkloadStateful);
	}

	private void handleBeforeScaleRequirements(HasMetadata workloadToScale) {
		for (String workloadName : getAnnotationValue(BEFORE_SCALE_REQUIREMENTS, workloadToScale, "").split(COMMA)) {
			if (isEmpty(workloadName)) {
				continue;
			}
			HasMetadata workload = workloadService.getWorkload(workloadToScale.getMetadata().getNamespace(), workloadName);
			if (workloadToScale == null) {
				throw new InterruptedProxyForwardException(String.format("the forwarder proxy could not identify the pre required workload %s/%s, dropping request", workloadToScale.getMetadata().getName(), workloadName));
			}
			log.info("pre-scale requirement : {}", getResourceNamespaceAndName(workload));
			scaleUp(workload, false);
		}
	}

	private io.fabric8.kubernetes.api.model.Service getTargetedScalingServiceByPod(Pod sourcePod, InetSocketAddress localAddress) {
		boolean sourcePodHasIstio = sourcePod.getSpec().getContainers().stream().anyMatch(c -> c.getName().equals(ISTIO_SIDECAR_CONTAINER_NAME));
		String containerNameForTcpTable = sourcePodHasIstio ? ISTIO_SIDECAR_CONTAINER_NAME : sourcePod.getSpec().getContainers().get(0).getName();

		String tcpTableRawOutput = podRepository.readFileFromPod(sourcePod, containerNameForTcpTable, "/proc/net/tcp");
		List<TcpTableEntry> tcpTableEntries = TcpTableParser.parseTCPTable(tcpTableRawOutput);
		log.trace("decoded entries : {}", tcpTableEntries);

		List<io.fabric8.kubernetes.api.model.Service> targetedServices = tcpTableEntries.stream()
				// filter non desired entries
				.filter(t -> t.getState().equals("1"))   // TCP_ESTABLISHED
				.filter(t -> !t.getUid().equals("1337")) // from our app, not from envoy !
				// more filtering : which may target our service
				.filter(t -> t.getRemotePort().equals(String.valueOf(localAddress.getPort())))
				// dummy sort : sort by inode number
				.sorted((e1, e2) -> {
					Integer inode1 = Integer.parseInt(e1.getInode());
					Integer inode2 = Integer.parseInt(e2.getInode());
					return inode2.compareTo(inode1); // reverse order !
				})
				.map(t -> t.getRemoteAddress())
				.distinct()
				// get source service
				.map(ip -> serviceRepository.findServiceByIp(ip))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(service -> IS_ALLOWED_TO_SCALE_LABEL_VALUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, service)))
				.collect(Collectors.toList());

		if (log.isDebugEnabled()) {
			log.debug("list of candidates : {}", targetedServices.stream().map(s -> getResourceNamespaceAndName(s)).collect(Collectors.toList()));
		}
		if (targetedServices.isEmpty()) {
			throw new InterruptedProxyForwardException("could not detected the service at the source of the request, aborting");
		}
		return targetedServices.get(0);
	}

}
