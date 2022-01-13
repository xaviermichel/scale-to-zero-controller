package io.neo9.scaler.access.models;

import java.net.InetSocketAddress;

import lombok.Data;

@Data
public class UpscalingContext {

	private InetSocketAddress proxyTargetAddress;

	private boolean loadInBackgroundAndReturnSplashScreenForward = false;
}
