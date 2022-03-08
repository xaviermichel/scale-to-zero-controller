package io.neo9.scaler.access.models;

import lombok.Data;

import java.net.InetSocketAddress;

@Data
public class UpscalingContext {

    private InetSocketAddress proxyTargetAddress;

    private boolean loadInBackgroundAndReturnSplashScreenForward = false;
}
