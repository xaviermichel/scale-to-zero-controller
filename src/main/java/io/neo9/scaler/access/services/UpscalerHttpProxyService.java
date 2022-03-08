package io.neo9.scaler.access.services;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.models.UpscalingContext;
import io.neo9.scaler.access.repositories.ServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;

import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.*;
import static io.neo9.scaler.access.utils.common.StringUtils.DOT;

@Service
@Slf4j
public class UpscalerHttpProxyService extends AbstractUpscalerProxyService {

    private final ServiceRepository serviceRepository;

    private final ScaleToZeroConfig scaleToZeroConfig;

    private final WorkloadService workloadService;

    private final PodService podService;

    @Value("${server.port}")
    private int serverPort;

    public UpscalerHttpProxyService(WorkloadService workloadService, ServiceRepository serviceRepository, ScaleToZeroConfig scaleToZeroConfig, WorkloadService workloadService1, PodService podService) {
        super(workloadService);
        this.serviceRepository = serviceRepository;
        this.scaleToZeroConfig = scaleToZeroConfig;
        this.workloadService = workloadService1;
        this.podService = podService;
    }

    /**
     * Takes destination virtual host, and return the endpoint to add to proxy chain
     */
    public UpscalingContext forwardRequest(UpscalingContext context, InetSocketAddress localAddress, InetSocketAddress remoteAddress, String host) {
        String fullHost = host;
        if (!fullHost.contains(DOT)) {
            Pod sourcePod = podService.getSourcePod(remoteAddress.getAddress().getHostAddress());
            log.info("forwarding request from {}", getResourceNamespaceAndName(sourcePod));
            fullHost = fullHost + DOT + sourcePod.getMetadata().getNamespace();
        }

        InetAddress hostAddress;
        try {
            hostAddress = InetAddress.getByName(fullHost);
        } catch (UnknownHostException e) {
            throw new InterruptedProxyForwardException(String.format("failed to resolve %s", fullHost), e);
        }

        Optional<io.fabric8.kubernetes.api.model.Service> targetedServiceOpt = serviceRepository.findServiceByIp(hostAddress.getHostAddress());
        if (targetedServiceOpt.isEmpty()) {
            throw new InterruptedProxyForwardException(String.format("did not find service with hostname : %s", fullHost));
        }
        io.fabric8.kubernetes.api.model.Service targetedService = targetedServiceOpt.get();

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
        } else {
            if (targetPod == null) {
                log.warn("cannot forward traffic on a null target pod, using self");
                context.setProxyTargetAddress(new InetSocketAddress("127.0.0.1", serverPort));
            } else {
                context.setProxyTargetAddress(new InetSocketAddress(targetPod.getStatus().getPodIP(), localAddress.getPort()));
            }
        }
        return context;
    }

}
