package io.neo9.scaler.access.controllers.web;

import java.net.URI;

import io.neo9.scaler.access.config.ScaleToZeroConfig;
import io.neo9.scaler.access.services.EnvironmentService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
@Slf4j
public class RedirectToSplashScreenController {

	private final ScaleToZeroConfig scaleToZeroConfig;

	private final EnvironmentService environmentService;

	public RedirectToSplashScreenController(ScaleToZeroConfig scaleToZeroConfig, EnvironmentService environmentService) {
		this.scaleToZeroConfig = scaleToZeroConfig;
		this.environmentService = environmentService;
	}

	@GetMapping
	public ResponseEntity redirectOnWait(@RequestHeader HttpHeaders sourceHeaders) {
		String envHost = sourceHeaders.getHost().getHostName();
		log.info("handling redirection to splash screen for {}", envHost);
		String envName = environmentService.getEnvironmentNameFromHost(envHost);

		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(URI.create(
				String.format("%s/wait.html?envUrl=http://%s&envName=%s",
						scaleToZeroConfig.getPublicUrl(),
						envHost,
						envName
				)));
		return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
	}

}
