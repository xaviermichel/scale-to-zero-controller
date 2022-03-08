package io.neo9.scaler.access.utils.network;

import io.neo9.scaler.access.models.UpscalingContext;
import io.neo9.scaler.access.services.UpscalerHttpProxyService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;

import java.net.InetSocketAddress;

import static io.neo9.scaler.access.utils.common.StringUtils.COLON;

@Slf4j
public class HttpServerProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final String HTTP_CLIENT_CODEC = "ssHttpClientCoded";
    public static final String HTTP_CODEC = "ssHttpCodec";
    public static final String HTTP_AGGREGATOR = "ssHttpAggregator";

    private static InternalLogger logger = InternalLoggerFactory.getInstance(HttpServerProxyHandler.class);

    private final UpscalerHttpProxyService upscalerHttpProxyService;

    private Channel clientChannel;

    private InetSocketAddress clientRecipient;

    private final InetSocketAddress localAddress;

    private final InetSocketAddress remoteAddress;

    public HttpServerProxyHandler(UpscalerHttpProxyService upscalerHttpProxyService, InetSocketAddress localAddress, InetSocketAddress remoteAddress) {
        this.upscalerHttpProxyService = upscalerHttpProxyService;
        this.localAddress = localAddress;
        this.remoteAddress = remoteAddress;
    }

    @Override
    public void channelRead0(ChannelHandlerContext clientCtx, FullHttpRequest request) {
        if (this.clientChannel == null) {
            this.clientChannel = clientCtx.channel();
        }

        String host = request.headers().get(HttpHeaders.HOST);
        if (host.contains(COLON)) {
            host = host.split(COLON)[0];
        }
        UpscalingContext upscalingContext = new UpscalingContext();
        upscalingContext = upscalerHttpProxyService.forwardRequest(upscalingContext, localAddress, remoteAddress, host);
        clientRecipient = upscalingContext.getProxyTargetAddress();

        logger.trace("channel id {}, host:{} => {}", clientChannel.id().toString(), host, clientRecipient);

        ReferenceCountUtil.retain(request);

        //Create client connection target machine
        connectToRemote(clientCtx, 3 * 60 * 1000).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if (channelFuture.isSuccess()) {
                    //The proxy server successfully connected to the target server
                    //Send message to target server
                    //Close long connection
                    request.headers().set(HttpHeaderNames.CONNECTION, "close");

                    //Forward request to target server
                    channelFuture.channel().writeAndFlush(request).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) throws Exception {
                            if (channelFuture.isSuccess()) {
                                //Remove the client's http codec
                                channelFuture.channel().pipeline().remove(HTTP_CLIENT_CODEC);
                                //Remove the http codec and aggregator between the proxy service and the requester channel
                                clientCtx.channel().pipeline().remove(HTTP_CODEC);
                                clientCtx.channel().pipeline().remove(HTTP_AGGREGATOR);
                                //After removal, let the channel directly become a simple ByteBuf transmission
                            }
                        }
                    });
                } else {
                    ReferenceCountUtil.retain(request);
                    clientCtx.writeAndFlush(getResponse(HttpResponseStatus.BAD_REQUEST, "The proxy service failed to connect to the remote service"))
                            .addListener(ChannelFutureListener.CLOSE);
                }
            }
        });
    }

    private DefaultFullHttpResponse getResponse(HttpResponseStatus statusCode, String message) {
        return new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, statusCode, Unpooled.copiedBuffer(message, CharsetUtil.UTF_8));
    }

    private ChannelFuture connectToRemote(ChannelHandlerContext ctx, int timeout) {
        return new Bootstrap().group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) {
                        ChannelPipeline pipeline = socketChannel.pipeline();
                        //Add http encoder
                        pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec());
                        //Add a data transmission channel
                        pipeline.addLast(new DataTransHandler(ctx.channel()));
                    }
                })
                .connect(clientRecipient.getAddress(), clientRecipient.getPort());
    }
}
