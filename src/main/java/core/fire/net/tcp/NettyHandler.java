/**
 * 
 */
package core.fire.net.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import core.fire.executor.TcpDispatcher;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 基于Netty实现的网络IO处理器。网络消息将被TcpDispatcher分发处理
 * 
 * @author lhl
 *
 *         2016年1月29日 下午5:36:23
 */
@Sharable
public class NettyHandler extends SimpleChannelInboundHandler<Packet>
{
    private static final Logger LOG = LoggerFactory.getLogger(NettyHandler.class);

    private TcpDispatcher dispatcher;

    public NettyHandler(TcpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOG.debug("", cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet msg) throws Exception {
        dispatcher.handle(ctx.channel(), msg);
    }

    public TcpDispatcher getDispatcher() {
        return dispatcher;
    }
}
