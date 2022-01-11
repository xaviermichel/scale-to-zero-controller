package io.neo9.scaler.access.services;

import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.exceptions.MissingLabelException;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;
import org.apache.commons.lang3.ObjectUtils;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getLabelsValues;
import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

@Service
public class WorkloadService {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	public WorkloadService(ScaleToZeroConfig scaleToZeroConfig, DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
	}


	@Nullable
	public HasMetadata getWorkload(String namespace, Map<String, String> applicationsIdentifierLabels) {
		Optional<Deployment> deploymentOpt = deploymentRepository.findOneByLabels(namespace, applicationsIdentifierLabels);
		Optional<StatefulSet> statefulSetOpt = statefulsetRepository.findOneByLabels(namespace, applicationsIdentifierLabels);

		return ObjectUtils.firstNonNull(deploymentOpt.orElse(null), statefulSetOpt.orElse(null));
	}

	public HasMetadata getWorkload(String namespace, String workloadName) {
		Optional<Deployment> deploymentOpt = deploymentRepository.findOne(namespace, workloadName);
		Optional<StatefulSet> statefulSetOpt = statefulsetRepository.findOne(namespace, workloadName);

		return ObjectUtils.firstNonNull(deploymentOpt.orElse(null), statefulSetOpt.orElse(null));
	}

	public Map<String, String> getWorkloadIdentifierLabels(HasMetadata hasMetadata) {
		try {
			return getLabelsValues(hasMetadata, scaleToZeroConfig.getApplicationIdentifierLabels());
		}
		catch (MissingLabelException e) {
			throw new InterruptedProxyForwardException(String.format("missing app identifier label on source %s : %s, aborting", getResourceNamespaceAndName(hasMetadata), scaleToZeroConfig.getApplicationIdentifierLabels()), e);
		}
	}
}
