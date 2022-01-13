package io.neo9.scaler.access.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvNameMatcher {

	private String regex;

	private String prefix;

	private String suffix;

}
