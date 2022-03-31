package io.neo9.scaler.access.config;

import lombok.Data;

@Data
public class ScaleDownCronConfig {

    private boolean enabled;

    private String expression;

    private String timezone;

}
