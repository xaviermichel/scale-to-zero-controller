package io.neo9.scaler.access.services;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.neo9.scaler.access.config.Annotations;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.models.UpscalingContext;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.PodRepository;
import io.neo9.scaler.access.repositories.ServiceRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import io.neo9.scaler.access.utils.common.KubernetesUtils;
import io.neo9.scaler.access.utils.network.TcpTableEntry;
import io.neo9.scaler.access.utils.network.TcpTableParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Annotations.BEFORE_SCALE_REQUIREMENTS;
import static io.neo9.scaler.access.config.Annotations.SHOW_SPLASH_SCREEN;
import static io.neo9.scaler.access.config.Commons.ISTIO_SIDECAR_CONTAINER_NAME;
import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getAnnotationValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getWorkloadIdentifierLabels;
import static io.neo9.scaler.access.utils.common.StringUtils.COMMA;
import static io.neo9.scaler.access.utils.common.StringUtils.EMPTY;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class UpscalerTcpProxyService {

	private final PodRepository podRepository;

	private final ServiceRepository serviceRepository;

	private final WorkloadService workloadService;

	private final PodService podService;

	private final ScaleToZeroConfig scaleToZeroConfig;

	@Value("${server.port}")
	private int serverPort;

	public UpscalerTcpProxyService(PodRepository podRepository, ServiceRepository serviceRepository, WorkloadService workloadService, PodService podService, ScaleToZeroConfig scaleToZeroConfig) {
		this.podRepository = podRepository;
		this.serviceRepository = serviceRepository;
		this.workloadService = workloadService;
		this.podService = podService;
		this.scaleToZeroConfig = scaleToZeroConfig;
	}

	/**
	 * Takes source and destination, and return the endpoint to add to proxy chain
	 */
	public UpscalingContext forwardRequest(UpscalingContext context, InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
		Pod sourcePod = podService.getSourcePod(remoteAddress.getAddress().getHostAddress());
		log.info("forwarding request from {}", getResourceNamespaceAndName(sourcePod));

		io.fabric8.kubernetes.api.model.Service targetedService = getTargetedScalingServiceByPod(sourcePod, localAddress);
		String serviceNamespaceAndName = getResourceNamespaceAndName(targetedService);
		log.debug("checking if workload behind {} [{}] have to upscale", serviceNamespaceAndName, targetedService.getSpec().getClusterIP());

		Map<String, String> applicationsIdentifierLabels = getWorkloadIdentifierLabels(targetedService, scaleToZeroConfig.getApplicationIdentifierLabels());

		HasMetadata workloadToScale = workloadService.getWorkload(targetedService.getMetadata().getNamespace(), applicationsIdentifierLabels);
		if (workloadToScale == null) {
			throw new InterruptedProxyForwardException(String.format("the forwarder proxy could not identify the deployment attached to %s, dropping request", serviceNamespaceAndName));
		}

		String workloadNamespaceAndName = getResourceNamespaceAndName(workloadToScale);
		log.debug("checking if {} needs to scale", workloadNamespaceAndName);
		if (!TRUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, workloadToScale))) {
			throw new InterruptedProxyForwardException(String.format("%s, does not have scaling label, won't do any action on it", workloadToScale));
		}

		Pod targetPod = scaleUp(context, workloadToScale);
		log.debug("{} have at least one replica required", workloadNamespaceAndName);

		if (context.isLoadInBackgroundAndReturnSplashScreenForward()) {
			context.setProxyTargetAddress(new InetSocketAddress("127.0.0.1", serverPort));
		}
		else {
			if (targetPod == null) {
				log.warn("cannot forward traffic on a null target pod, using self");
				context.setProxyTargetAddress(new InetSocketAddress("127.0.0.1", serverPort));
			}
			else {
				context.setProxyTargetAddress(new InetSocketAddress(targetPod.getStatus().getPodIP(), localAddress.getPort()));
			}
		}

		return context;
	}

	private Pod scaleUp(UpscalingContext context, HasMetadata workloadToScale) {
		return scaleUp(context, workloadToScale, true);
	}

	private Pod scaleUp(UpscalingContext context, HasMetadata workloadToScale, boolean waitForScaleFinished) {
		handleBeforeScaleRequirements(context, workloadToScale);

		if (workloadService.getReplicaCount(workloadToScale) == 0) {
			workloadService.scale(workloadToScale, workloadService.getOriginalReplicaCount(workloadToScale), !context.isLoadInBackgroundAndReturnSplashScreenForward());
			workloadService.unannotated(workloadToScale, List.of(Annotations.ORIGINAL_REPLICA));
		}

		if (!waitForScaleFinished || context.isLoadInBackgroundAndReturnSplashScreenForward()) {
			return null;
		}

		return workloadService.waitForWorkloadToBeReady(workloadToScale);
	}

	private void handleBeforeScaleRequirements(UpscalingContext context, HasMetadata workloadToScale) {
		if (TRUE.equals(getAnnotationValue(SHOW_SPLASH_SCREEN, workloadToScale))) {
			log.info("splash screen required for workload {}, will finish loading in background", getResourceNamespaceAndName(workloadToScale));
			context.setLoadInBackgroundAndReturnSplashScreenForward(true);
		}

		List<HasMetadata> workloadsToScale = stream(getAnnotationValue(BEFORE_SCALE_REQUIREMENTS, workloadToScale, EMPTY).split(COMMA))
				.map(String::trim)
				.filter(StringUtils::isNotBlank)
				.map(workloadName -> {
					HasMetadata workload = workloadService.getWorkload(workloadToScale.getMetadata().getNamespace(), workloadName);
					if (workload == null) {
						throw new InterruptedProxyForwardException(String.format("the forwarder proxy could not identify the pre required workload %s/%s, dropping request", workloadToScale.getMetadata().getName(), workloadName));
					}
					return workload;
				})
				.collect(toList());

		// 1st loop to start all workloads without wait (background)
		for (HasMetadata requiredWorkload : workloadsToScale) {
			log.info("pre-scale requirement, starting : {}", getResourceNamespaceAndName(requiredWorkload));
			scaleUp(context, requiredWorkload, false);
		}

		if (context.isLoadInBackgroundAndReturnSplashScreenForward()) {
			return;
		}

		// 2nd loop to wait each workload to have the expected result
		for (HasMetadata requiredWorkload : workloadsToScale) {
			log.info("pre-scale requirement, waiting for : {}", getResourceNamespaceAndName(requiredWorkload));
			workloadService.waitForWorkloadToBeReady(requiredWorkload);
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
				.peek(e -> log.debug("one candidate was : {}", e))
				.map(TcpTableEntry::getRemoteAddress)
				.distinct()
				// get source service
				.map(serviceRepository::findServiceByIp)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(service -> TRUE.equals(getLabelValue(IS_ALLOWED_TO_SCALE_LABEL_KEY, service)))
				.filter(workloadService::isHijacked)
				.collect(toList());

		if (log.isDebugEnabled()) {
			log.debug("list of candidates : {}", targetedServices.stream().map(KubernetesUtils::getResourceNamespaceAndName).collect(toList()));
		}
		if (targetedServices.isEmpty()) {
			throw new InterruptedProxyForwardException("could not detected the service at the source of the request, aborting");
		}
		return targetedServices.get(0);
	}

}
