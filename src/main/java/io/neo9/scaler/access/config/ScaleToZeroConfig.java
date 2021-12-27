package io.neo9.scaler.access.config;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scaler")
public class ScaleToZeroConfig {

	@Setter
	@Getter
	private Set<String> applicationIdentifierLabels;

}
