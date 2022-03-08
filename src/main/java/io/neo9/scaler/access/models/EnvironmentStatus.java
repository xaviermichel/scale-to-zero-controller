package io.neo9.scaler.access.models;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EnvironmentStatus {

    private String name;

    private List<WorkloadStatus> workloadStatuses;
}
