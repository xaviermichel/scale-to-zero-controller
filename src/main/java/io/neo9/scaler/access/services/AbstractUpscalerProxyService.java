package io.neo9.scaler.access.services;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.neo9.scaler.access.config.Annotations;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.models.UpscalingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.neo9.scaler.access.config.Annotations.BEFORE_SCALE_REQUIREMENTS;
import static io.neo9.scaler.access.config.Annotations.SHOW_SPLASH_SCREEN;
import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getAnnotationValue;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;
import static io.neo9.scaler.access.utils.common.StringUtils.COMMA;
import static io.neo9.scaler.access.utils.common.StringUtils.EMPTY;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

@Service
@Slf4j
public class AbstractUpscalerProxyService {

    private final WorkloadService workloadService;

    public AbstractUpscalerProxyService(WorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    protected Pod scaleUp(UpscalingContext context, HasMetadata workloadToScale) {
        return scaleUp(context, workloadToScale, true);
    }

    protected Pod scaleUp(UpscalingContext context, HasMetadata workloadToScale, boolean waitForScaleFinished) {
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

    protected void handleBeforeScaleRequirements(UpscalingContext context, HasMetadata workloadToScale) {
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

}
