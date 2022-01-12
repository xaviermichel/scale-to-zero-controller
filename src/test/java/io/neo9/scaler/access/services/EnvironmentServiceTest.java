package io.neo9.scaler.access.services;

import java.util.List;

import io.neo9.scaler.access.config.EnvNameMatcher;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnvironmentServiceTest {

	@Test
	public void shouldReturnDefaultIfNoMatch() {
		// given
		ScaleToZeroConfig scaleToZeroConfig = new ScaleToZeroConfig();
		scaleToZeroConfig.setEnvNameMatchers(List.of(EnvNameMatcher.builder().regex("^.*\\.(.*)\\.neokube\\.neo9\\.pro$").prefix("dev-").suffix("").build()));
		EnvironmentService environmentService = new EnvironmentService(null, null, null, scaleToZeroConfig);

		// when
		String envName = environmentService.getEnvironmentNameFromHost("special-env.neokube.neo9.pro");

		// then
		assertThat(envName).isEqualTo("default");
	}

	@Test
	public void shouldExtractRightEnvironmentName() {
		// given
		ScaleToZeroConfig scaleToZeroConfig = new ScaleToZeroConfig();
		scaleToZeroConfig.setEnvNameMatchers(List.of(EnvNameMatcher.builder().regex("^.*\\.(.*)\\.neokube\\.neo9\\.pro$").prefix("dev-").suffix("").build()));
		EnvironmentService environmentService = new EnvironmentService(null, null, null, scaleToZeroConfig);

		// when
		String envName = environmentService.getEnvironmentNameFromHost("app-with-splash-screen.my-super-env.neokube.neo9.pro");

		// then
		assertThat(envName).isEqualTo("dev-my-super-env");
	}

	@Test
	public void shouldExtractTheRightEnvironmentName() {
		// given
		ScaleToZeroConfig scaleToZeroConfig = new ScaleToZeroConfig();
		scaleToZeroConfig.setEnvNameMatchers(List.of(
				EnvNameMatcher.builder().regex("^.*\\.(.*)\\.dev\\.neokube\\.neo9\\.pro$").prefix("").suffix("-dev").build(),
				EnvNameMatcher.builder().regex("^.*\\.(.*)\\.neokube\\.neo9\\.pro$").prefix("").suffix("").build()
		));
		EnvironmentService environmentService = new EnvironmentService(null, null, null, scaleToZeroConfig);

		// when
		String envNameDev = environmentService.getEnvironmentNameFromHost("app.xmichel.dev.neokube.neo9.pro");
		String envNameRec = environmentService.getEnvironmentNameFromHost("app.staging.neokube.neo9.pro");

		// then
		assertThat(envNameDev).isEqualTo("xmichel-dev");
		assertThat(envNameRec).isEqualTo("staging");
	}

}
