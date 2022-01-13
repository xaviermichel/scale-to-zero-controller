package io.neo9.scaler.access.config;

import java.util.List;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
	private List<EnvNameMatcher> envNameMatchers;

	public List<EnvNameMatcher> envNameMatchers() {
		return envNameMatchers;
	}
}
