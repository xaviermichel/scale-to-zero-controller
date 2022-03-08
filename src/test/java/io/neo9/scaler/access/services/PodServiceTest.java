package io.neo9.scaler.access.services;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.repositories.PodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PodServiceTest {

	@Mock
	private PodRepository podRepository;

	@Test
	public void shouldGetLogIfNoExclusion() {
		// given
		String podLog = "{\"method\":\"GET\",\"request-id\":\"(undefined)\",\"path\":\"/\",\"status\":\"200\",\"durationMs\":0.594,\"totalDurationMs\":1.144,\"content-length\":\"12\",\"requestId\":\"q_ma5LQXu\",\"level\":\"info\",\"message\":\"api call /\",\"label\":\"annonces-api:n9-node-routing\",\"timestamp\":\"2022-01-14T15:12:45.857Z\"}\n"
				+ "{\"method\":\"GET\",\"request-id\":\"(undefined)\",\"path\":\"/\",\"status\":\"200\",\"durationMs\":0.596,\"totalDurationMs\":1.205,\"content-length\":\"12\",\"requestId\":\"pcoa-ZTRA\",\"level\":\"info\",\"message\":\"api call /\",\"label\":\"annonces-api:n9-node-routing\",\"timestamp\":\"2022-01-14T15:13:15.857Z\"}\n"
				+ "{\"method\":\"GET\",\"request-id\":\"(undefined)\",\"path\":\"/\",\"status\":\"200\",\"durationMs\":0.586,\"totalDurationMs\":1.15,\"content-length\":\"12\",\"requestId\":\"IpS3zNbf-\",\"level\":\"info\",\"message\":\"api call /\",\"label\":\"annonces-api:n9-node-routing\",\"timestamp\":\"2022-01-14T15:13:22.432Z\"}\n"
				+ "{\"method\":\"GET\",\"request-id\":\"(undefined)\",\"path\":\"/\",\"status\":\"200\",\"durationMs\":0.621,\"totalDurationMs\":1.25,\"content-length\":\"12\",\"requestId\":\"FM3nA5Q8I\",\"level\":\"info\",\"message\":\"api call /\",\"label\":\"annonces-api:n9-node-routing\",\"timestamp\":\"2022-01-14T15:13:25.857Z\"}\n";

		Pod p1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").and().build();
		when(podRepository.getLogsSince(p1, 60)).thenReturn(podLog);

		PodService podService = new PodService(podRepository, new ScaleToZeroConfig());

		// when
		String logsSince = podService.getLogsSince(p1, 60, null);

		// then
		assertThat(logsSince).isEqualTo(podLog);
	}

	@Test
	public void shouldGetLogWithSimpleExclusion() {
		// given
		String podLog = "GET /health 200 15 - 0.241 ms\n"
				+ "GET /health 200 15 - 0.224 ms\n"
				+ "GET /health 200 15 - 0.286 ms\n"
				+ "GET /health 200 15 - 0.225 ms\n"
				+ "GET /ping 200 15 - 0.225 ms\n"
				+ "GET /health 200 15 - 0.217 ms\n"
				+ "GET /test 200 15 - 0.223 ms\n"
				+ "GET /health 200 15 - 0.213 ms\n"
				+ "GET /health 200 15 - 0.225 ms\n"
				+ "GET /ping 200 15 - 0.225 ms\n"
				+ "GET /health 200 15 - 0.239 ms\n"
				+ "GET /health 200 15 - 0.215 ms\n"
				+ "GET / 200 15 - 0.271 ms\n"
				+ "GET /health 200 15 - 0.219 ms\n"
				+ "GET /health 200 15 - 0.249 ms\n";

		Pod p1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").and().build();
		when(podRepository.getLogsSince(p1, 60)).thenReturn(podLog);

		PodService podService = new PodService(podRepository, new ScaleToZeroConfig());

		// when
		String logsSince = podService.getLogsSince(p1, 60, "GET /health.*");

		// then
		assertThat(logsSince).isEqualTo("GET /ping 200 15 - 0.225 ms\n"
				+ "GET /test 200 15 - 0.223 ms\n"
				+ "GET /ping 200 15 - 0.225 ms\n"
				+ "GET / 200 15 - 0.271 ms\n");
	}

	@Test
	public void shouldGetLogWithMoreComplexExclusion() {
		// given
		String podLog = "GET /health 200 15 - 0.241 ms\n"
				+ "GET /health 200 15 - 0.224 ms\n"
				+ "GET /health 200 15 - 0.286 ms\n"
				+ "GET /health 200 15 - 0.225 ms\n"
				+ "GET /ping 200 15 - 0.225 ms\n"
				+ "GET /health 200 15 - 0.217 ms\n"
				+ "GET /test 200 15 - 0.223 ms\n"
				+ "GET /health 200 15 - 0.213 ms\n"
				+ "GET /health 200 15 - 0.225 ms\n"
				+ "GET /ping 200 15 - 0.225 ms\n"
				+ "GET /health 200 15 - 0.239 ms\n"
				+ "GET /health 200 15 - 0.215 ms\n"
				+ "GET / 200 15 - 0.271 ms\n"
				+ "GET /health 200 15 - 0.219 ms\n"
				+ "GET /health 200 15 - 0.249 ms\n";

		Pod p1 = new PodBuilder().withNewMetadata().withName("pod1").withNamespace("test").and().build();
		when(podRepository.getLogsSince(p1, 60)).thenReturn(podLog);

		PodService podService = new PodService(podRepository, new ScaleToZeroConfig());

		// when
		String logsSince = podService.getLogsSince(p1, 60, "GET (/health|/ping).*");

		// then
		assertThat(logsSince).isEqualTo("GET /test 200 15 - 0.223 ms\n"
				+ "GET / 200 15 - 0.271 ms\n");
	}

}
