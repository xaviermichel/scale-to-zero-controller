package io.neo9.scaler.access.controllers.tcp;

import java.util.concurrent.TimeUnit;

import io.neo9.scaler.access.services.UpscalerTcpProxyService;
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class RequestHandler {

	private final UpscalerTcpProxyService upscalerTcpProxyService;

	public RequestHandler(UpscalerTcpProxyService upscalerTcpProxyService) {
		this.upscalerTcpProxyService = upscalerTcpProxyService;
	}

	@Bean
	public ChannelFuture tcpServer() {
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
						upscalerTcpProxyService.forwardRequest(ctx);
					}
				});

		log.info("listen at {}:{}", "0.0.0.0", 8080);
		return tcpBootstrap.bind("0.0.0.0", 8080);
	}

}
