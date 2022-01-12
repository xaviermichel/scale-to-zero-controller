package io.neo9.scaler.access.models;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvironmentStatus {

	private String name;

	private List<WorkloadStatus> workloadStatuses;
}
