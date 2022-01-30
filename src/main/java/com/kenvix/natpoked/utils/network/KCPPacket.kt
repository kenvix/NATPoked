package com.kenvix.natpoked.utils.network

import io.netty.buffer.ByteBuf

data class KCPPacket(val data: ByteBuf, val size: Int)
