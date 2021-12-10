package io.neo9.scaler.access.exceptions;

import lombok.Getter;

// TODO : pwet
public class ResourceNotManagedByOperatorException extends RuntimeException {

	@Getter
	private final String resourceNamespaceName;

	public ResourceNotManagedByOperatorException(String resourceNamespaceName) {
		super(String.format("should not manipulate resource %s . It sounds not managed by operator (labels not detected %s=%s)", resourceNamespaceName, "pwet:MANAGED_BY_OPERATOR_KEY", "pwet:MANAGED_BY_OPERATOR_VALUE"));
		this.resourceNamespaceName = resourceNamespaceName;
	}

}
