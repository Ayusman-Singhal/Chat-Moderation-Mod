package com.arhenniuss.chatmod;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;
import javax.net.ssl.SSLException;

public class ServerCommunication {
    private static final String HOST = "https://mute-mod-api01.onrender.com"; // Replace with your Render app name
    private static final int PORT = 443; // Use 443 for WSS (WebSocket Secure)

    private Channel channel;
    private WebSocketClientHandshaker handshaker;

    public void connect() {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            final SslContext sslCtx = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE).build();

            URI uri = new URI("wss://" + HOST);
            handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());

            Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(sslCtx.newHandler(ch.alloc(), HOST, PORT));
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpObjectAggregator(8192));
                        pipeline.addLast(new WebSocketClientProtocolHandler(handshaker));
                        pipeline.addLast(new SimpleChannelInboundHandler<WebSocketFrame>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
                                if (frame instanceof TextWebSocketFrame) {
                                    System.out.println("Received from server: " + ((TextWebSocketFrame) frame).text());
                                }
                            }
                        });
                    }
                });

            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            channel = future.channel();
            handshaker.handshake(channel).sync();
            System.out.println("Connected to Render.com WebSocket server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMuteMessage(String senderName, String muteCommand) {
        if (channel != null && channel.isActive()) {
            String message = "MUTE:" + senderName + ":" + muteCommand;
            channel.writeAndFlush(new TextWebSocketFrame(message));
        } else {
            System.out.println("Not connected to server");
        }
    }

    public void disconnect() {
        if (channel != null) {
            channel.writeAndFlush(new CloseWebSocketFrame());
            channel.close();
        }
    }
}
