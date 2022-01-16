package io.neo9.scaler.access.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.neo9.scaler.access.config.EnvNameMatcher;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.models.EnvironmentStatus;
import io.neo9.scaler.access.models.WorkloadStatus;
import io.neo9.scaler.access.repositories.DeploymentRepository;
import io.neo9.scaler.access.repositories.StatefulsetRepository;

import org.springframework.stereotype.Service;

import static io.neo9.scaler.access.config.Commons.TRUE;
import static io.neo9.scaler.access.config.Labels.IS_ALLOWED_TO_SCALE_LABEL_KEY;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.util.StringUtils.uncapitalize;

@Service
public class EnvironmentService {

	private final DeploymentRepository deploymentRepository;

	private final StatefulsetRepository statefulsetRepository;

	private final WorkloadService workloadService;

	private final ScaleToZeroConfig scaleToZeroConfig;

	public EnvironmentService(DeploymentRepository deploymentRepository, StatefulsetRepository statefulsetRepository, WorkloadService workloadService, ScaleToZeroConfig scaleToZeroConfig) {
		this.deploymentRepository = deploymentRepository;
		this.statefulsetRepository = statefulsetRepository;
		this.workloadService = workloadService;
		this.scaleToZeroConfig = scaleToZeroConfig;
	}

	public EnvironmentStatus getEnvironmentStatus(String namespace) {
		List<WorkloadStatus> workloadStatuses = new ArrayList<>();

		for (Deployment deployment : deploymentRepository.findAllInNamespace(namespace, Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))) {
			WorkloadStatus workloadStatus = WorkloadStatus.builder()
					.kind(uncapitalize(deployment.getKind()))
					.name(deployment.getMetadata().getName())
					.isReady(workloadService.isReady(deployment))
					.isStarted(workloadService.isStarted(deployment))
					.build();
			workloadStatuses.add(workloadStatus);
		}

		for (StatefulSet statefulSet : statefulsetRepository.findAllInNamespace(namespace, Map.of(IS_ALLOWED_TO_SCALE_LABEL_KEY, TRUE))) {
			WorkloadStatus workloadStatus = WorkloadStatus.builder()
					.kind(uncapitalize(statefulSet.getKind()))
					.name(statefulSet.getMetadata().getName())
					.isReady(workloadService.isReady(statefulSet))
					.isStarted(workloadService.isStarted(statefulSet))
					.build();
			workloadStatuses.add(workloadStatus);
		}

		return EnvironmentStatus.builder()
				.name(namespace)
				.workloadStatuses(workloadStatuses)
				.build();
	}

	public String getEnvironmentNameFromHost(String envHost) {
		for (EnvNameMatcher envNameMatcher : scaleToZeroConfig.getEnvNameMatchers()) {
			Pattern pattern = Pattern.compile(envNameMatcher.getRegex());
			Matcher matcher = pattern.matcher(envHost);
			if (matcher.find()) {
				String prefix = isNotBlank(envNameMatcher.getPrefix()) ? envNameMatcher.getPrefix() : EMPTY;
				String suffix = isNotBlank(envNameMatcher.getSuffix()) ? envNameMatcher.getSuffix() : EMPTY;
				return String.format("%s%s%s", prefix, matcher.group(1), suffix);
			}
		}
		return "default";
	}
}
