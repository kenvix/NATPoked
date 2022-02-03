package com.kenvix.web.utils

import io.netty.buffer.ByteBuf

fun ByteBuf.readerIndexInArrayOffset() = readerIndex() + arrayOffset()
fun ByteBuf.writerIndexInArrayOffset() = writerIndex() + arrayOffset()