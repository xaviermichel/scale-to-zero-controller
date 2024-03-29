package io.neo9.scaler.access.utils.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.TimeUnit;

public class TcpServerProxyHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private static InternalLogger logger = InternalLoggerFactory.getInstance(TcpServerProxyHandler.class);

    private final InetSocketAddress clientRecipient;

    private Channel clientChannel;

    private Channel remoteChannel;

    private Bootstrap proxyClient;

    private List<ByteBuf> clientBuffs;

    public TcpServerProxyHandler(InetSocketAddress clientRecipient) {
        this.clientRecipient = clientRecipient;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext clientCtx, ByteBuf msg) {
        if (this.clientChannel == null) {
            this.clientChannel = clientCtx.channel();
        }
        logger.trace("channel id {}, readableBytes:{}", clientChannel.id().toString(), msg.readableBytes());
        proxy(msg);
    }

    private void proxy(ByteBuf msg) {
        logger.trace("channel id {}, pc is null {}, {}", clientChannel.id().toString(), (remoteChannel == null), msg.readableBytes());
        if (remoteChannel == null && proxyClient == null) {
            proxyClient = new Bootstrap();

            proxyClient.group(clientChannel.eventLoop()).channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 60 * 1000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.SO_RCVBUF, 2 * 1024 * 1024)
                    .option(ChannelOption.SO_SNDBUF, 2 * 1024 * 1024)
                    .option(ChannelOption.TCP_NODELAY, false)
                    .handler(
                            new ChannelInitializer<>() {
                                @Override
                                protected void initChannel(Channel ch) {
                                    ch.pipeline()
                                            .addLast("timeout", new IdleStateHandler(0, 0, 300, TimeUnit.SECONDS) {
                                                @Override
                                                protected IdleStateEvent newIdleStateEvent(IdleState state, boolean first) {
                                                    logger.trace("{} state: {}", clientRecipient.toString(), state.toString());
                                                    proxyChannelClose();
                                                    return super.newIdleStateEvent(state, first);
                                                }
                                            })
                                            .addLast("tcpProxy", new SimpleChannelInboundHandler<ByteBuf>() {
                                                @Override
                                                protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                                    clientChannel.writeAndFlush(msg.retain());
                                                }

                                                @Override
                                                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                                    super.channelInactive(ctx);
                                                    proxyChannelClose();
                                                }

                                                @Override
                                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                                    logger.error("Error while proxyfying client", cause);
                                                    proxyChannelClose();
                                                }
                                            });
                                }
                            }
                    );
            try {
                proxyClient
                        .connect(clientRecipient)
                        .addListener((ChannelFutureListener) future -> {
                            try {
                                if (future.isSuccess()) {
                                    logger.trace("channel id {}, {}<->{}<->{} connect  {}", clientChannel.id().toString(), clientChannel.remoteAddress().toString(), future.channel().localAddress().toString(), clientRecipient.toString(), future.isSuccess());
                                    remoteChannel = future.channel();
                                    if (clientBuffs != null) {
                                        ListIterator<ByteBuf> bufsIterator = clientBuffs.listIterator();
                                        while (bufsIterator.hasNext()) {
                                            remoteChannel.writeAndFlush(bufsIterator.next());
                                        }
                                        clientBuffs = null;
                                    }
                                } else {
                                    logger.error("channel id {}, {}<->{} connect {},cause {}", clientChannel.id().toString(), clientChannel.remoteAddress().toString(), clientRecipient.toString(), future.isSuccess(), future.cause());
                                    proxyChannelClose();
                                }
                            } catch (Exception e) {
                                proxyChannelClose();
                            }
                        });
            } catch (Exception e) {
                logger.error("connect internet error", e);
                proxyChannelClose();
                return;
            }
        }

        if (remoteChannel == null) {
            if (clientBuffs == null) {
                clientBuffs = new ArrayList<>();
            }
            clientBuffs.add(msg.retain());
        } else {
            if (clientBuffs == null) {
                remoteChannel.writeAndFlush(msg.retain());
            } else {
                clientBuffs.add(msg.retain());
            }
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        proxyChannelClose();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Error while proxyfying client", cause);
        proxyChannelClose();
    }

    private void proxyChannelClose() {
        logger.trace("closing chanel");
        try {
            if (clientBuffs != null) {
                clientBuffs.forEach(ReferenceCountUtil::release);
                clientBuffs = null;
            }
            if (remoteChannel != null) {
                remoteChannel.close();
                remoteChannel = null;
            }
            if (clientChannel != null) {
                clientChannel.close();
                clientChannel = null;
            }
        } catch (Exception e) {
            logger.error("close channel error", e);
        }
    }

}
