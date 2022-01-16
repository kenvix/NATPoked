package com.kenvix.natpoked.test

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.util.CharsetUtil


object NetTest {
    private const val R_PORT = 12222 //Reciever的端口

    @JvmStatic
    fun main(args: Array<String>) {
        //1.NioEventLoopGroup是执行者
        //1.NioEventLoopGroup是执行者
        val group = NioEventLoopGroup()
        println("NioEventLoopGroup in main :$group")
        //2.启动器
        //2.启动器
        val bootstrap = Bootstrap()
        //3.配置启动器
        //3.配置启动器
        bootstrap.group(group) //3.1指定group
            .channel(NioDatagramChannel::class.java) //3.2指定channel
            .option(ChannelOption.SO_BROADCAST, true) //3.3指定为广播模式
            .handler(object : ChannelInitializer<NioDatagramChannel>() {
                @Throws(Exception::class)
                override fun initChannel(nioDatagramChannel: NioDatagramChannel) {
                    nioDatagramChannel.pipeline().addLast(MyUdpDecoder()) //3.4在pipeline中加入解码器
                }
            })
        try {
            //4.bind到指定端口，并返回一个channel，该端口就是监听UDP报文的端口
            val channel: Channel = bootstrap.bind(R_PORT).sync().channel()
            //5.等待channel的close
            channel.closeFuture().sync()
            //6.关闭group
            group.shutdownGracefully()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private class MyUdpDecoder : MessageToMessageDecoder<DatagramPacket>() {
        @Throws(java.lang.Exception::class)
        override fun decode(
            channelHandlerContext: ChannelHandlerContext,
            datagramPacket: DatagramPacket,
            list: List<Any>
        ) {
            val buf: ByteBuf = datagramPacket.content()
            val msg = buf.toString(CharsetUtil.UTF_8)
            println("UdpReciever :$msg")
        }
    }
}