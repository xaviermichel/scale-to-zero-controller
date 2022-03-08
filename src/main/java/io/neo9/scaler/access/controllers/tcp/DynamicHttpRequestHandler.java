package io.neo9.scaler.access.controllers.tcp;

import io.neo9.scaler.access.services.UpscalerHttpProxyService;
import io.neo9.scaler.access.utils.network.HttpServerProxyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

import static io.neo9.scaler.access.utils.network.HttpServerProxyHandler.HTTP_AGGREGATOR;
import static io.neo9.scaler.access.utils.network.HttpServerProxyHandler.HTTP_CODEC;

@Slf4j
@Component
public class DynamicHttpRequestHandler {

    private final UpscalerHttpProxyService upscalerHttpProxyService;

    private final Map<Integer, ChannelFuture> startedHttpHandlers;

    public DynamicHttpRequestHandler(UpscalerHttpProxyService upscalerHttpProxyService) {
        this.startedHttpHandlers = new HashMap<>();
        this.upscalerHttpProxyService = upscalerHttpProxyService;
    }

    public void startServer(Integer listenPort) {
        if (startedHttpHandlers.containsKey(listenPort)) {
            return;
        }

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        ServerBootstrap httpBootstrap = new ServerBootstrap();
        httpBootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 5120)
                .option(ChannelOption.TCP_NODELAY, false)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ctx) {
                        log.debug("handling new client connection from {}", ctx.remoteAddress().toString());
                        ChannelPipeline p = ctx.pipeline();
                        p.addLast(HTTP_CODEC, new HttpServerCodec());
                        p.addLast(HTTP_AGGREGATOR, new HttpObjectAggregator(100 * 1024 * 1024));
                        p.addLast("ssHttpProxy", new HttpServerProxyHandler(upscalerHttpProxyService, ctx.localAddress(), ctx.remoteAddress()));
                    }
                });

        ChannelFuture bind = httpBootstrap.bind("0.0.0.0", listenPort);
        log.info("started http server, listening on {}:{}", "0.0.0.0", listenPort);
        startedHttpHandlers.put(listenPort, bind);
    }

}
