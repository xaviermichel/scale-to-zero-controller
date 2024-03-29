package io.neo9.scaler.access.services;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.config.Annotations;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import io.neo9.scaler.access.utils.common.KubernetesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.neo9.scaler.access.config.Annotations.*;
import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.*;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

@Service
@Slf4j
public class DownscalingService {

    private final ScaleToZeroConfig scaleToZeroConfig;

    private final DeploymentRepository deploymentRepository;

    private final StatefulsetRepository statefulsetRepository;

    private final PodService podService;

    private final WorkloadService workloadService;

    public DownscalingService(ScaleToZeroConfig scaleToZeroConfig, DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, PodService podService, WorkloadService workloadService) {
        this.scaleToZeroConfig = scaleToZeroConfig;
        this.deploymentRepository = deploymentRepository;
        this.statefulsetRepository = statefulsetRepository;
        this.podService = podService;
        this.workloadService = workloadService;
    }

    @Scheduled(cron = "${scaler.scaleDownCron.expression}", zone ="${scaler.scaleDownCron.timezone}")
    public void scaleDownCron() {
        log.info("scale down cron");
        if (! scaleToZeroConfig.scaleDownCron().isEnabled()) {
            return;
        }
        List<HasMetadata> workloads = collectScalableWorkloads();
        log.info("will explore shut down possibilities for {}", workloads.stream().map(KubernetesUtils::getResourceNamespaceAndName).collect(toList()));
        for (HasMetadata workload : workloads) {
            scaleDown(workload);
        }
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
    public void scaleDownFixedDelayLoop() {
        log.info("scale down fixed delay loop");
        List<HasMetadata> workloads = collectScalableWorkloads();
        log.info("will explore shut down possibilities for {}", workloads.stream().map(KubernetesUtils::getResourceNamespaceAndName).collect(toList()));
        for (HasMetadata workload : workloads) {
            if (isNotEmpty(getAnnotationValue(DOWNSCALE_LOG_STRATEGY, workload))) {
                scaleDownOnLogActivityStrategy(workload);
            }
            // more strategies may come here
        }
    }

    private List<HasMetadata> collectScalableWorkloads() {
        List<HasMetadata> workloads = new ArrayList<>();
        for (Deployment deployment : deploymentRepository.findAllInAnyNamespace(Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))) {
            if (haveAnyAnnotation(deployment, ALL_DOWNSCALE_STRATEGY) && workloadService.isStarted(deployment)) {
                workloads.add(deployment);
            }
        }
        for (StatefulSet statefulSet : statefulsetRepository.findAllInAnyNamespace(Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))) {
            if (haveAnyAnnotation(statefulSet, ALL_DOWNSCALE_STRATEGY) && workloadService.isStarted(statefulSet)) {
                workloads.add(statefulSet);
            }
        }
        return workloads;
    }

    public void scaleDownOnLogActivityStrategy(HasMetadata workload) {
        Integer downScaleLogTimeoutInMinutes = Integer.parseInt(getAnnotationValue(DOWNSCALE_LOG_STRATEGY, workload).trim());
        List<Pod> pods = podService.listPodsAssociatedToWorkload(workload);
        boolean shouldDownscale = true;
        for (Pod pod : pods) {
            String activityLog = podService.getLogsSince(pod, downScaleLogTimeoutInMinutes, getAnnotationValue(DOWNSCALE_LOG_STRATEGY_EXCLUDE_PATTERN, workload));
            if (isNotEmpty(activityLog)) {
                shouldDownscale = false;
                break;
            }
        }

        if (shouldDownscale) {
            scaleDown(workload);
        }
    }

    public HasMetadata scaleDown(HasMetadata workloadToScale) {
        if (!workloadService.isStarted(workloadToScale)) {
            return workloadToScale;
        }
        log.info("going to scale down : {}", getResourceNamespaceAndName(workloadToScale));
        workloadToScale = workloadService.annotate(workloadToScale, Map.of(Annotations.ORIGINAL_REPLICA, String.valueOf(workloadService.getReplicaCount(workloadToScale))));
        return workloadService.scale(workloadToScale, 0, true);
    }

}
