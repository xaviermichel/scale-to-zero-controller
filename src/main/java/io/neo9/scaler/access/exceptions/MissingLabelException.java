package io.neo9.scaler.access.exceptions;

import io.fabric8.kubernetes.api.model.HasMetadata;

import static io.neo9.scaler.access.utils.common.KubernetesUtils.getResourceNamespaceAndName;

public class MissingLabelException extends Exception {

    public MissingLabelException(String label, HasMetadata hasMetadata) {
        super(String.format("the label %s is missing on %s", label, getResourceNamespaceAndName(hasMetadata)));
    }

}
