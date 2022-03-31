package io.neo9.scaler.access.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

@Component
@ConfigurationProperties(prefix = "scaler")
public class ScaleToZeroConfig {

    @Getter
    @Setter
    private Set<String> applicationIdentifierLabels;

    @Getter
    @Setter
    private String publicUrl;

    @Setter
    @Getter
    private List<EnvNameMatcher> envNameMatchers;

    @Setter
    private List<KeyValueWrapper> onHijackAnnotationsToAdd;

    @Setter
    private ScaleDownCronConfig scaleDownCron;

    public Map<String, String> getOnHijackAnnotationsToAdd() {
        return onHijackAnnotationsToAdd.stream().collect(toMap(w -> w.getKey(), w -> w.getValue()));
    }

    @Setter
    @Getter
    private List<String> onReleaseAnnotationsToRemove;

    @Setter
    @Getter
    private List<String> onUpscaleFallbackOriginalReplicasAnnotations;

    @Setter
    @Getter
    private Integer defaultOnUpscaleReplicaCount;

    public ScaleDownCronConfig scaleDownCron() {
        return scaleDownCron;
    }
}
