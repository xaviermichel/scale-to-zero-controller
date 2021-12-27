package io.neo9.scaler.access.controllers.tcp;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.neo9.scaler.access.exceptions.InterruptedProxyForwardException;
import io.neo9.scaler.access.services.UpscalerTcpProxyService;
import io.neo9.scaler.access.utils.network.TcpServerProxyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DynamicRequestHandler {

	private final UpscalerTcpProxyService upscalerTcpProxyService;

	private final Map<Integer, ChannelFuture> startedTcpHandlers;

	public DynamicRequestHandler(UpscalerTcpProxyService upscalerTcpProxyService) {
		this.startedTcpHandlers = new HashMap<>();
		this.upscalerTcpProxyService = upscalerTcpProxyService;
	}

	public void startTcpServer(Integer listenPort) {
		if (startedTcpHandlers.containsKey(listenPort)) {
			return;
		}

		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();

		ServerBootstrap tcpBootstrap = new ServerBootstrap();
		tcpBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
				.option(ChannelOption.SO_BACKLOG, 5120)
				.option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024)
				.childOption(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024)
				.childOption(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024)
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				.childOption(ChannelOption.TCP_NODELAY, false)
				.childOption(ChannelOption.SO_LINGER, 1)
				.childHandler(new ChannelInitializer<NioSocketChannel>() {
					@Override
					protected void initChannel(NioSocketChannel ctx) {
						log.debug("handling new client connection from {}", ctx.remoteAddress().toString());
						ctx.pipeline()
								//timeout
								.addLast("timeout", new IdleStateHandler(0, 0, 300, TimeUnit.SECONDS) {
									@Override
									protected IdleStateEvent newIdleStateEvent(IdleState state, boolean first) {
										ctx.close();
										return super.newIdleStateEvent(state, first);
									}
								});
						try {
							InetSocketAddress clientRecipient = upscalerTcpProxyService.forwardRequest(ctx.localAddress(), ctx.remoteAddress());
							ctx.pipeline().addLast("ssTcpProxy", new TcpServerProxyHandler(clientRecipient));
						}
						catch (InterruptedProxyForwardException interruptedProxyForwardException) {
							log.warn("Failed to forward request : {}", interruptedProxyForwardException.getMessage(), interruptedProxyForwardException);
							ctx.close();
						}
					}
				});


		ChannelFuture bind = tcpBootstrap.bind("0.0.0.0", listenPort);
		log.info("started tcp server, listening on {}:{}", "0.0.0.0", listenPort);
		startedTcpHandlers.put(listenPort, bind);
	}

}
