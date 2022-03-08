package io.neo9.scaler.access.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkloadStatus {

    private String kind;

    private String name;

    private Boolean isStarted;

    private Boolean isReady;

}
