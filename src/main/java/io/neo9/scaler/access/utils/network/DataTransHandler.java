package io.neo9.scaler.access.utils.network;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

public class DataTransHandler extends ChannelInboundHandlerAdapter {

    private Channel channel;

    public DataTransHandler(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!channel.isOpen()) {
            ReferenceCountUtil.release(msg);
            return;
        }
        channel.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //The target server is disconnected from the proxy server
        //The proxy server is also disconnected from the original server
        if (channel != null) {
            //Send an empty buf and close the channel through listener monitoring to ensure that the data transmission in the channel is completed
            channel.writeAndFlush(PooledByteBufAllocator.DEFAULT.buffer()).addListener(ChannelFutureListener.CLOSE);
        }
        super.channelInactive(ctx);
    }
}
