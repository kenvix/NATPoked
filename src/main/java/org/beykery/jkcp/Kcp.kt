/**
 * KCP - A Better ARQ Protocol Implementation
 * skywind3000 (at) gmail.com, 2010-2011
 * Features:
 * + Average RTT reduce 30% - 40% vs traditional ARQ like tcp.
 * + Maximum RTT reduce three times vs tcp.
 * + Lightweight, distributed as a single source file.
 */
package org.beykery.jkcp

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import java.lang.RuntimeException
import java.util.ArrayDeque
import java.util.ArrayList

/**
 * @author beykery
 */
class Kcp(output: Output, user: Any?) {
    /**
     * conv
     *
     * @return
     */
    /**
     * conv
     *
     * @param conv
     */
    var conv = 0
    private var mtu: Int
    private var mss: Int
    private var state = 0
    private var snd_una = 0
    private var snd_nxt = 0
    private var rcv_nxt = 0
    private val ts_recent = 0
    private val ts_lastack = 0
    private var ssthresh: Int
    private var rx_rttval = 0
    private var rx_srtt = 0
    private var rx_rto: Int
    private var rx_minrto: Int
    private var snd_wnd: Int
    private var rcv_wnd: Int
    private var rmt_wnd: Int
    private var cwnd = 0
    private var probe = 0
    private var current = 0
    private var interval: Int
    private var ts_flush: Int
    private var xmit = 0
    private var nodelay = 0
    private var updated = 0
    private var ts_probe = 0
    private var probe_wait = 0
    private val dead_link: Int
    private var incr = 0
    private val snd_queue = ArrayDeque<Segment>()
    private val rcv_queue = ArrayDeque<Segment>()
    private val snd_buf = ArrayList<Segment>()
    private val rcv_buf = ArrayList<Segment>()
    private val acklist = ArrayList<Int>()
    private var buffer: ByteBuf?
    private var fastresend = 0
    private var nocwnd = 0
    var isStream //流模式
            = false
    private val output: Output
    val user //远端地址
            : Any?
    var nextUpdate //the next update time.
            : Long = 0

    /**
     * SEGMENT
     */
    internal inner class Segment constructor(size: Int) {
        var conv = 0
        var cmd: Byte = 0
        var frg = 0
        var wnd = 0
        var ts = 0
        var sn = 0
        var una = 0
        var resendts = 0
        var rto = 0
        var fastack = 0
        var xmit = 0
        var data: ByteBuf? = null

        init {
            if (size > 0) {
                data = PooledByteBufAllocator.DEFAULT.buffer(size)
            }
        }

        /**
         * encode a segment into buffer
         *
         * @param buf
         * @return
         */
        fun encode(buf: ByteBuf?): Int {
            val off = buf!!.writerIndex()
            buf.writeIntLE(conv)
            buf.writeByte(cmd.toInt())
            buf.writeByte(frg)
            buf.writeShortLE(wnd)
            buf.writeIntLE(ts)
            buf.writeIntLE(sn)
            buf.writeIntLE(una)
            buf.writeIntLE(data?.readableBytes() ?: 0)
            return buf.writerIndex() - off
        }

        /**
         * 释放内存
         */
        fun release() {
            if (data != null && data!!.refCnt() > 0) {
                data!!.release(data!!.refCnt())
            }
        }
    }

    /**
     * create a new kcpcb
     *
     * @param output
     * @param user
     */
    init {
        snd_wnd = IKCP_WND_SND
        rcv_wnd = IKCP_WND_RCV
        rmt_wnd = IKCP_WND_RCV
        mtu = IKCP_MTU_DEF
        mss = mtu - IKCP_OVERHEAD
        rx_rto = IKCP_RTO_DEF
        rx_minrto = IKCP_RTO_MIN
        interval = IKCP_INTERVAL
        ts_flush = IKCP_INTERVAL
        ssthresh = IKCP_THRESH_INIT
        dead_link = IKCP_DEADLINK
        buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
        this.output = output
        this.user = user
    }

    /**
     * check the size of next message in the recv queue
     *
     * @return
     */
    fun peekSize(): Int {
        if (rcv_queue.isEmpty()) {
            return -1
        }
        val seq = rcv_queue.first
        if (seq.frg == 0) {
            return seq.data!!.readableBytes()
        }
        if (rcv_queue.size < seq.frg + 1) {
            return -1
        }
        var length = 0
        for (item in rcv_queue) {
            length += item.data!!.readableBytes()
            if (item.frg == 0) {
                break
            }
        }
        return length
    }

    /**
     * user/upper level recv: returns size, returns below zero for EAGAIN
     *
     * @param buffer
     * @return
     */
    fun receive(buffer: ByteBuf): Int {
        if (rcv_queue.isEmpty()) {
            return -1
        }
        val peekSize = peekSize()
        if (peekSize < 0) {
            return -2
        }
        val recover = rcv_queue.size >= rcv_wnd
        // merge fragment.
        var c = 0
        var len = 0
        for (seg in rcv_queue) {
            len += seg.data!!.readableBytes()
            buffer.writeBytes(seg.data)
            c++
            if (seg.frg == 0) {
                break
            }
        }
        if (c > 0) {
            for (i in 0 until c) {
                rcv_queue.removeFirst().data!!.release()
            }
        }
        if (len != peekSize) {
            throw RuntimeException("数据异常.")
        }
        // move available data from rcv_buf -> rcv_queue
        c = 0
        for (seg in rcv_buf) {
            if (seg.sn == rcv_nxt && rcv_queue.size < rcv_wnd) {
                rcv_queue.add(seg)
                rcv_nxt++
                c++
            } else {
                break
            }
        }
        if (c > 0) {
            for (i in 0 until c) {
                rcv_buf.removeAt(0)
            }
        }
        // fast recover
        if (rcv_queue.size < rcv_wnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe = probe or IKCP_ASK_TELL
        }
        return len
    }

    /**
     * user/upper level send, returns below zero for error
     *
     * @param buffer
     * @return
     */
    fun send(buffer: ByteBuf): Int {
        if (buffer.readableBytes() == 0) {
            return -1
        }
        // append to previous segment in streaming mode (if possible)
        if (isStream && !snd_queue.isEmpty()) {
            val seg = snd_queue.last
            if (seg.data != null && seg.data!!.readableBytes() < mss) {
                val capacity = mss - seg.data!!.readableBytes()
                val extend = Math.min(buffer.readableBytes(), capacity)
                seg.data!!.writeBytes(buffer, extend)
                if (buffer.readableBytes() == 0) {
                    return 0
                }
            }
        }
        var count: Int
        count = if (buffer.readableBytes() <= mss) {
            1
        } else {
            (buffer.readableBytes() + mss - 1) / mss
        }
        if (count > 255) {
            return -2
        }
        if (count == 0) {
            count = 1
        }
        //fragment
        for (i in 0 until count) {
            val size = Math.min(buffer.readableBytes(), mss)
            val seg: Segment = Segment(size)
            seg.data!!.writeBytes(buffer, size)
            seg.frg = if (isStream) 0 else count - i - 1
            snd_queue.add(seg)
        }
        buffer.release()
        return 0
    }

    /**
     * update ack.
     *
     * @param rtt
     */
    private fun update_ack(rtt: Int) {
        if (rx_srtt == 0) {
            rx_srtt = rtt
            rx_rttval = rtt / 2
        } else {
            var delta = rtt - rx_srtt
            if (delta < 0) {
                delta = -delta
            }
            rx_rttval = (3 * rx_rttval + delta) / 4
            rx_srtt = (7 * rx_srtt + rtt) / 8
            if (rx_srtt < 1) {
                rx_srtt = 1
            }
        }
        val rto = rx_srtt + Math.max(interval, 4 * rx_rttval)
        rx_rto = _ibound_(rx_minrto, rto, IKCP_RTO_MAX)
    }

    private fun shrink_buf() {
        snd_una = if (snd_buf.size > 0) {
            snd_buf[0].sn
        } else {
            snd_nxt
        }
    }

    private fun parse_ack(sn: Int) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return
        }
        for (i in snd_buf.indices) {
            val seg = snd_buf[i]
            if (sn == seg.sn) {
                snd_buf.removeAt(i)
                seg.data!!.release(seg.data!!.refCnt())
                break
            }
            if (_itimediff(sn, seg.sn) < 0) {
                break
            }
        }
    }

    private fun parse_una(una: Int) {
        var c = 0
        for (seg in snd_buf) {
            if (_itimediff(una, seg.sn) > 0) {
                c++
            } else {
                break
            }
        }
        if (c > 0) {
            for (i in 0 until c) {
                val seg = snd_buf.removeAt(0)
                seg.data!!.release(seg.data!!.refCnt())
            }
        }
    }

    private fun parse_fastack(sn: Int) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return
        }
        for (seg in snd_buf) {
            if (_itimediff(sn, seg.sn) < 0) {
                break
            } else if (sn != seg.sn) {
                seg.fastack++
            }
        }
    }

    /**
     * ack append
     *
     * @param sn
     * @param ts
     */
    private fun ack_push(sn: Int, ts: Int) {
        acklist.add(sn)
        acklist.add(ts)
    }

    private fun parse_data(newseg: Segment) {
        val sn = newseg.sn
        if (_itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || _itimediff(sn, rcv_nxt) < 0) {
            newseg.release()
            return
        }
        val n = rcv_buf.size - 1
        var temp = -1
        var repeat = false
        for (i in n downTo 0) {
            val seg = rcv_buf[i]
            if (seg.sn == sn) {
                repeat = true
                break
            }
            if (_itimediff(sn, seg.sn) > 0) {
                temp = i
                break
            }
        }
        if (!repeat) {
            rcv_buf.add(temp + 1, newseg)
        } else {
            newseg.release()
        }
        // move available data from rcv_buf -> rcv_queue
        var c = 0
        for (seg in rcv_buf) {
            if (seg.sn == rcv_nxt && rcv_queue.size < rcv_wnd) {
                rcv_queue.add(seg)
                rcv_nxt++
                c++
            } else {
                break
            }
        }
        if (0 < c) {
            for (i in 0 until c) {
                rcv_buf.removeAt(0)
            }
        }
    }

    /**
     * when you received a low level packet (eg. UDP packet), call it
     *
     * @param data
     * @return
     */
    fun input(data: ByteBuf?): Int {
        val una_temp = snd_una
        var flag = 0
        var maxack = 0
        if (data == null || data.readableBytes() < IKCP_OVERHEAD) {
            return -1
        }
        while (true) {
            var readed = false
            var ts: Int
            var sn: Int
            var len: Int
            var una: Int
            var conv_: Int
            var wnd: Int
            var cmd: Byte
            var frg: Byte
            if (data.readableBytes() < IKCP_OVERHEAD) {
                break
            }
            conv_ = data.readIntLE()
            if (conv != conv_) {
                return -1
            }
            cmd = data.readByte()
            frg = data.readByte()
            wnd = data.readShortLE().toInt()
            ts = data.readIntLE()
            sn = data.readIntLE()
            una = data.readIntLE()
            len = data.readIntLE()
            if (data.readableBytes() < len) {
                return -2
            }
            when (cmd.toInt()) {
                IKCP_CMD_PUSH, IKCP_CMD_ACK, IKCP_CMD_WASK, IKCP_CMD_WINS -> {}
                else -> return -3
            }
            rmt_wnd = wnd and 0x0000ffff
            parse_una(una)
            shrink_buf()
            when (cmd.toInt()) {
                IKCP_CMD_ACK -> {
                    if (_itimediff(current, ts) >= 0) {
                        update_ack(_itimediff(current, ts))
                    }
                    parse_ack(sn)
                    shrink_buf()
                    if (flag == 0) {
                        flag = 1
                        maxack = sn
                    } else if (_itimediff(sn, maxack) > 0) {
                        maxack = sn
                    }
                }
                IKCP_CMD_PUSH -> if (_itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
                    ack_push(sn, ts)
                    if (_itimediff(sn, rcv_nxt) >= 0) {
                        val seg: Segment = Segment(len)
                        seg.conv = conv_
                        seg.cmd = cmd
                        seg.frg = frg.toInt() and 0x000000ff
                        seg.wnd = wnd
                        seg.ts = ts
                        seg.sn = sn
                        seg.una = una
                        if (len > 0) {
                            seg.data!!.writeBytes(data, len)
                            readed = true
                        }
                        parse_data(seg)
                    }
                }
                IKCP_CMD_WASK ->                     // ready to send back IKCP_CMD_WINS in Ikcp_flush
                    // tell remote my window size
                    probe = probe or IKCP_ASK_TELL
                IKCP_CMD_WINS -> {}
                else -> return -3
            }
            if (!readed) {
                data.skipBytes(len)
            }
        }
        if (flag != 0) {
            parse_fastack(maxack)
        }
        if (_itimediff(snd_una, una_temp) > 0) {
            if (cwnd < rmt_wnd) {
                if (cwnd < ssthresh) {
                    cwnd++
                    incr += mss
                } else {
                    if (incr < mss) {
                        incr = mss
                    }
                    incr += mss * mss / incr + mss / 16
                    if ((cwnd + 1) * mss <= incr) {
                        cwnd++
                    }
                }
                if (cwnd > rmt_wnd) {
                    cwnd = rmt_wnd
                    incr = rmt_wnd * mss
                }
            }
        }
        return 0
    }

    private fun wnd_unused(): Int {
        return if (rcv_queue.size < rcv_wnd) {
            rcv_wnd - rcv_queue.size
        } else 0
    }

    /**
     * force flush
     */
    fun forceFlush() {
        val cur = current
        var change = 0
        var lost = 0
        val seg: Segment = Segment(0)
        seg.conv = conv
        seg.cmd = IKCP_CMD_ACK.toByte()
        seg.wnd = wnd_unused()
        seg.una = rcv_nxt
        // flush acknowledges
        var c = acklist.size / 2
        for (i in 0 until c) {
            if (buffer!!.readableBytes() + IKCP_OVERHEAD > mtu) {
                output.out(buffer, this, user)
                buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
            }
            seg.sn = acklist[i * 2 + 0]
            seg.ts = acklist[i * 2 + 1]
            seg.encode(buffer)
        }
        acklist.clear()
        // probe window size (if remote window size equals zero)
        if (rmt_wnd == 0) {
            if (probe_wait == 0) {
                probe_wait = IKCP_PROBE_INIT
                ts_probe = current + probe_wait
            } else if (_itimediff(current, ts_probe) >= 0) {
                if (probe_wait < IKCP_PROBE_INIT) {
                    probe_wait = IKCP_PROBE_INIT
                }
                probe_wait += probe_wait / 2
                if (probe_wait > IKCP_PROBE_LIMIT) {
                    probe_wait = IKCP_PROBE_LIMIT
                }
                ts_probe = current + probe_wait
                probe = probe or IKCP_ASK_SEND
            }
        } else {
            ts_probe = 0
            probe_wait = 0
        }
        // flush window probing commands
        if (probe and IKCP_ASK_SEND != 0) {
            seg.cmd = IKCP_CMD_WASK.toByte()
            if (buffer!!.readableBytes() + IKCP_OVERHEAD > mtu) {
                output.out(buffer, this, user)
                buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
            }
            seg.encode(buffer)
        }
        // flush window probing commands
        if (probe and IKCP_ASK_TELL != 0) {
            seg.cmd = IKCP_CMD_WINS.toByte()
            if (buffer!!.readableBytes() + IKCP_OVERHEAD > mtu) {
                output.out(buffer, this, user)
                buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
            }
            seg.encode(buffer)
        }
        probe = 0
        // calculate window size
        var cwnd_temp = Math.min(snd_wnd, rmt_wnd)
        if (nocwnd == 0) {
            cwnd_temp = Math.min(cwnd, cwnd_temp)
        }
        // move data from snd_queue to snd_buf
        c = 0
        for (item in snd_queue) {
            if (_itimediff(snd_nxt, snd_una + cwnd_temp) >= 0) {
                break
            }
            item.conv = conv
            item.cmd = IKCP_CMD_PUSH.toByte()
            item.wnd = seg.wnd
            item.ts = cur
            item.sn = snd_nxt++
            item.una = rcv_nxt
            item.resendts = cur
            item.rto = rx_rto
            item.fastack = 0
            item.xmit = 0
            snd_buf.add(item)
            c++
        }
        if (c > 0) {
            for (i in 0 until c) {
                snd_queue.removeFirst()
            }
        }
        // calculate resent
        val resent = if (fastresend > 0) fastresend else Int.MAX_VALUE
        val rtomin = if (nodelay == 0) rx_rto shr 3 else 0
        // flush data segments
        for (segment in snd_buf) {
            var needsend = false
            if (segment.xmit == 0) {
                needsend = true
                segment.xmit++
                segment.rto = rx_rto
                segment.resendts = cur + segment.rto + rtomin
            } else if (_itimediff(cur, segment.resendts) >= 0) {
                needsend = true
                segment.xmit++
                xmit++
                if (nodelay == 0) {
                    segment.rto += rx_rto
                } else {
                    segment.rto += rx_rto / 2
                }
                segment.resendts = cur + segment.rto
                lost = 1
            } else if (segment.fastack >= resent) {
                needsend = true
                segment.xmit++
                segment.fastack = 0
                segment.resendts = cur + segment.rto
                change++
            }
            if (needsend) {
                segment.ts = cur
                segment.wnd = seg.wnd
                segment.una = rcv_nxt
                val need = IKCP_OVERHEAD + segment.data!!.readableBytes()
                if (buffer!!.readableBytes() + need > mtu) {
                    output.out(buffer, this, user)
                    buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
                }
                segment.encode(buffer)
                if (segment.data!!.readableBytes() > 0) {
                    buffer!!.writeBytes(segment.data!!.duplicate())
                }
                if (segment.xmit >= dead_link) {
                    state = -1
                }
            }
        }
        // flash remain segments
        if (buffer!!.readableBytes() > 0) {
            output.out(buffer, this, user)
            buffer = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
        }
        // update ssthresh
        if (change != 0) {
            val inflight = snd_nxt - snd_una
            ssthresh = inflight / 2
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN
            }
            cwnd = ssthresh + resent
            incr = cwnd * mss
        }
        if (lost != 0) {
            ssthresh = cwnd / 2
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN
            }
            cwnd = 1
            incr = mss
        }
        if (cwnd < 1) {
            cwnd = 1
            incr = mss
        }
    }

    /**
     * flush pending data
     */
    fun flush() {
        //if (updated != 0)
        run { forceFlush() }
    }

    /**
     * update state (call it repeatedly, every 10ms-100ms), or you can ask
     * ikcp_check when to call it again (without ikcp_input/_send calling).
     *
     * @param current current timestamp in millisec.
     */
    fun update(current: Long) {
        this.current = current.toInt()
        if (updated == 0) {
            updated = 1
            ts_flush = this.current
        }
        var slap = _itimediff(this.current, ts_flush)
        if (slap >= 10000 || slap < -10000) {
            ts_flush = this.current
            slap = 0
        }
        if (slap >= 0) {
            ts_flush += interval
            if (_itimediff(this.current, ts_flush) >= 0) {
                ts_flush = this.current + interval
            }
            flush()
        }
    }

    /**
     * Determine when should you invoke ikcp_update: returns when you should
     * invoke ikcp_update in millisec, if there is no ikcp_input/_send calling.
     * you can call ikcp_update in that time, instead of call update repeatly.
     * Important to reduce unnacessary ikcp_update invoking. use it to schedule
     * ikcp_update (eg. implementing an epoll-like mechanism, or optimize
     * ikcp_update when handling massive kcp connections)
     *
     * @param current
     * @return
     */
    fun check(current: Long): Long {
        val cur = current
        if (updated == 0) {
            return cur
        }
        var ts_flush_temp = ts_flush.toLong()
        var tm_packet: Long = 0x7fffffff
        if (_itimediff(cur, ts_flush_temp) >= 10000 || _itimediff(cur, ts_flush_temp) < -10000) {
            ts_flush_temp = cur
        }
        if (_itimediff(cur, ts_flush_temp) >= 0) {
            return cur
        }
        val tm_flush = _itimediff(ts_flush_temp, cur)
        for (seg in snd_buf) {
            val diff = _itimediff(seg.resendts.toLong(), cur)
            if (diff <= 0) {
                return cur
            }
            if (diff < tm_packet) {
                tm_packet = diff
            }
        }
        var minimal = if (tm_packet < tm_flush) tm_packet else tm_flush
        if (minimal >= interval) {
            minimal = interval.toLong()
        }
        return cur + minimal
    }

    /**
     * change MTU size, default is 1400
     *
     * @param mtu
     * @return
     */
    fun setMtu(mtu: Int): Int {
        if (mtu < 50 || mtu < IKCP_OVERHEAD) {
            return -1
        }
        val buf = PooledByteBufAllocator.DEFAULT.buffer((mtu + IKCP_OVERHEAD) * 3)
        this.mtu = mtu
        mss = mtu - IKCP_OVERHEAD
        if (buffer != null) {
            buffer!!.release()
        }
        buffer = buf
        return 0
    }

    /**
     * interval per update
     *
     * @param interval
     * @return
     */
    fun interval(interval: Int): Int {
        var interval = interval
        if (interval > 5000) {
            interval = 5000
        } else if (interval < 10) {
            interval = 10
        }
        this.interval = interval
        return 0
    }

    /**
     * fastest: ikcp_nodelay(kcp, 1, 20, 2, 1) nodelay: 0:disable(default),
     * 1:enable interval: internal update timer interval in millisec, default is
     * 100ms resend: 0:disable fast resend(default), 1:enable fast resend nc:
     * 0:normal congestion control(default), 1:disable congestion control
     *
     * @param nodelay
     * @param interval
     * @param resend
     * @param nc
     * @return
     */
    fun noDelay(nodelay: Int, interval: Int, resend: Int, nc: Int): Int {
        var interval = interval
        if (nodelay >= 0) {
            this.nodelay = nodelay
            rx_minrto = if (nodelay != 0) {
                IKCP_RTO_NDL
            } else {
                IKCP_RTO_MIN
            }
        }
        if (interval >= 0) {
            if (interval > 5000) {
                interval = 5000
            } else if (interval < 10) {
                interval = 10
            }
            this.interval = interval
        }
        if (resend >= 0) {
            fastresend = resend
        }
        if (nc >= 0) {
            nocwnd = nc
        }
        return 0
    }

    /**
     * set maximum window size: sndwnd=32, rcvwnd=32 by default
     *
     * @param sndwnd
     * @param rcvwnd
     * @return
     */
    fun wndSize(sndwnd: Int, rcvwnd: Int): Int {
        if (sndwnd > 0) {
            snd_wnd = sndwnd
        }
        if (rcvwnd > 0) {
            rcv_wnd = rcvwnd
        }
        return 0
    }

    /**
     * get how many packet is waiting to be sent
     *
     * @return
     */
    fun waitSnd(): Int {
        return snd_buf.size + snd_queue.size
    }

    fun setMinRto(min: Int) {
        rx_minrto = min
    }

    override fun toString(): String {
        return user.toString()
    }

    /**
     * 释放内存
     */
    fun release() {
        if (buffer!!.refCnt() > 0) {
            buffer!!.release(buffer!!.refCnt())
        }
        for (seg in rcv_buf) {
            seg.release()
        }
        for (seg in rcv_queue) {
            seg.release()
        }
        for (seg in snd_buf) {
            seg.release()
        }
        for (seg in snd_queue) {
            seg.release()
        }
    }

    companion object {
        const val IKCP_RTO_NDL = 30 // no delay min rto
        const val IKCP_RTO_MIN = 100 // normal min rto
        const val IKCP_RTO_DEF = 200
        const val IKCP_RTO_MAX = 60000
        const val IKCP_CMD_PUSH = 81 // cmd: push data
        const val IKCP_CMD_ACK = 82 // cmd: ack
        const val IKCP_CMD_WASK = 83 // cmd: window probe (ask)
        const val IKCP_CMD_WINS = 84 // cmd: window size (tell)
        const val IKCP_ASK_SEND = 1 // need to send IKCP_CMD_WASK
        const val IKCP_ASK_TELL = 2 // need to send IKCP_CMD_WINS
        const val IKCP_WND_SND = 32
        const val IKCP_WND_RCV = 32
        const val IKCP_MTU_DEF = 1400
        const val IKCP_ACK_FAST = 3
        const val IKCP_INTERVAL = 100
        const val IKCP_OVERHEAD = 24
        const val IKCP_DEADLINK = 10
        const val IKCP_THRESH_INIT = 2
        const val IKCP_THRESH_MIN = 2
        const val IKCP_PROBE_INIT = 7000 // 7 secs to probe window size
        const val IKCP_PROBE_LIMIT = 120000 // up to 120 secs to probe window
        private fun _ibound_(lower: Int, middle: Int, upper: Int): Int {
            return Math.min(Math.max(lower, middle), upper)
        }

        private fun _itimediff(later: Int, earlier: Int): Int {
            return later - earlier
        }

        private fun _itimediff(later: Long, earlier: Long): Long {
            return later - earlier
        }
    }
}