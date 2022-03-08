package io.neo9.scaler.access.services;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.discovery.v1beta1.EndpointSlice;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.repositories.EndpointSliceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelsValues;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
@Slf4j
public class WorkloadHijackingService {

    private final EndpointSliceHijackingService endpointSliceHijackingService;

    private final EndpointSliceRepository endpointSliceRepository;

    private final ScaleToZeroConfig scaleToZeroConfig;

    private final WorkloadService workloadService;

    public WorkloadHijackingService(EndpointSliceHijackingService endpointSliceHijackingService, EndpointSliceRepository endpointSliceRepository, ScaleToZeroConfig scaleToZeroConfig, WorkloadService workloadService) {
        this.endpointSliceHijackingService = endpointSliceHijackingService;
        this.endpointSliceRepository = endpointSliceRepository;
        this.scaleToZeroConfig = scaleToZeroConfig;
        this.workloadService = workloadService;
    }

    public void releaseIfNecessary(HasMetadata hasMetadata) throws MissingLabelException {
        if (workloadService.isReady(hasMetadata)) {
            log.info("releasing hijack on {}", getResourceNamespaceAndName(hasMetadata));
            Map<String, String> appIdentifierLabels = getLabelsValues(hasMetadata, scaleToZeroConfig.getApplicationIdentifierLabels());

            List<EndpointSlice> endpointSlicesOfDeployment = endpointSliceRepository.findAllWithLabels(hasMetadata.getMetadata().getNamespace(), appIdentifierLabels);
            endpointSlicesOfDeployment.forEach(e -> endpointSliceHijackingService.releaseHijackedIfNecessary(e));

            workloadService.unannotated(hasMetadata, scaleToZeroConfig.getOnReleaseAnnotationsToRemove());
        }
    }
}
