package io.neo9.scaler.access.controllers.rest;

import io.neo9.scaler.access.models.EnvironmentStatus;
import io.neo9.scaler.access.services.EnvironmentService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("environments")
@Slf4j
public class EnvironmentController {

	private final EnvironmentService environmentService;

	public EnvironmentController(EnvironmentService environmentService) {
		this.environmentService = environmentService;
	}

	@GetMapping("/{namespace}")
	public EnvironmentStatus getEnvironment(@PathVariable String namespace) {
		return environmentService.getEnvironmentStatus(namespace);
	}
}
