package com.kenvix.natpoked.utils.network;

import java.util.*;

public abstract class KCP {

    //参考 https://github.com/hkspirt/kcp-java进行修改。
    //更注重贴近原版kcp，作为java基础版本对照

    //=====================================================================
    // KCP BASIC
    //=====================================================================
    public final int IKCP_RTO_NDL = 30;   // no delay min rto
    public final int IKCP_RTO_MIN = 100;  // normal min rto
    public final int IKCP_RTO_DEF = 200;
    public final int IKCP_RTO_MAX = 60000;
    public final int IKCP_CMD_PUSH = 81;  // cmd: push data
    public final int IKCP_CMD_ACK = 82;   // cmd: ack
    public final int IKCP_CMD_WASK = 83;  // cmd: window probe (ask)
    public final int IKCP_CMD_WINS = 84;  // cmd: window size (tell)
    public final int IKCP_ASK_SEND = 1;   // need to send IKCP_CMD_WASK
    public final int IKCP_ASK_TELL = 2;   // need to send IKCP_CMD_WINS
    public final int IKCP_WND_SND = 32;
    public final int IKCP_WND_RCV = 128;  // must >= max fragment size
    public final int IKCP_MTU_DEF = 1400;
    public final int IKCP_ACK_FAST = 3;
    public final int IKCP_INTERVAL = 100;
    public final int IKCP_OVERHEAD = 24;
    public final int IKCP_DEADLINK = 10;
    public final int IKCP_THRESH_INIT = 2;
    public final int IKCP_THRESH_MIN = 2;
    public final int IKCP_PROBE_INIT = 7000;    // 7 secs to probe window size
    public final int IKCP_PROBE_LIMIT = 120000; // up to 120 secs to probe window


    public final int IKCP_LOG_OUTPUT = 1;
    public final int IKCP_LOG_INPUT = 2;
    public final int IKCP_LOG_SEND = 4;
    public final int IKCP_LOG_RECV = 8;
    public final int IKCP_LOG_IN_DATA = 16;
    public final int IKCP_LOG_IN_ACK = 32;
    public final int IKCP_LOG_IN_PROBE = 64;
    public final int IKCP_LOG_IN_WINS = 128;
    public final int IKCP_LOG_OUT_DATA = 256;
    public final int IKCP_LOG_OUT_ACK = 512;
    public final int IKCP_LOG_OUT_PROBE = 1024;
    public final int IKCP_LOG_OUT_WINS = 2048;

    protected abstract void output(byte[] buffer, int size); // 需具体实现

    //采用小端编码： https://github.com/skywind3000/kcp/issues/53

    /**
     * encode 8 bits unsigned int
     */
    public static void ikcp_encode8u(byte[] p, int offset, byte c) {
        p[0 + offset] = c;
    }

    /**
     * decode 8 bits unsigned int
     */
    public static byte ikcp_decode8u(byte[] p, int offset) {
        return p[0 + offset];
    }

    /**
     * encode 16 bits unsigned int (msb)
     */
    public static void ikcp_encode16u(byte[] p, int offset, int w) {
        p[offset + 0] = (byte) (w & 0xff);
        p[offset + 1] = (byte) ((w >>> 8) & 0xff);
    }

    /**
     * decode 16 bits unsigned int (msb)
     */
    public static int ikcp_decode16u(byte[] p, int offset) {
        int x1 = ((int) p[offset + 0]) & 0xff;
        int x2 = ((int) p[offset + 1]) & 0xff;
        return ((x2 << 8) | x1) & 0xffff;
    }

    /**
     * encode 32 bits unsigned int (msb)
     */
    public static void ikcp_encode32u(byte[] p, int offset, long l) {
        p[offset + 0] = (byte) (l & 0xff);
        p[offset + 1] = (byte) ((l >>> 8) & 0xff);
        p[offset + 2] = (byte) ((l >>> 16) & 0xff);
        p[offset + 3] = (byte) ((l >>> 24) & 0xff);
    }

    /**
     * decode 32 bits unsigned int (msb)
     */
    public static long ikcp_decode32u(byte[] p, int offset) {
        int x1 = ((int) p[offset + 0]) & 0xff;
        int x2 = ((int) p[offset + 1]) & 0xff;
        int x3 = ((int) p[offset + 2]) & 0xff;
        int x4 = ((int) p[offset + 3]) & 0xff;
        int x5 = (x1) | (x2 << 8) | (x3 << 16) | (x4 << 24);
        return ((long) x5) & 0xffffffff;
    }

    static long _imin_(long a, long b) {
        return a <= b ? a : b;
    }

    static long _imax_(long a, long b) {
        return a >= b ? a : b;
    }

    static long _ibound_(long lower, long middle, long upper) {
        return _imin_(_imax_(lower, middle), upper);
    }

    static int _itimediff(long later, long earlier) {
        return ((int) (later - earlier));
    }

    // write log
    void ikcp_log(int mask, String fmt, Object... args) {
        if ((mask & this.logmask) == 0)
            return;
        String str = String.format(fmt, args);
        this.writelog(str, user);
    }

    // check log mask
    int ikcp_canlog(int mask) {
        if ((mask & this.logmask) == 0)
            return 0;
        return 1;
    }

    // output segment
    int ikcp_output(byte[] data, int size) {
        if (ikcp_canlog(IKCP_LOG_OUTPUT) != 0) {
            ikcp_log(IKCP_LOG_OUTPUT, "[RO] %ld bytes", (long) data.length);
        }
        if (data.length == 0)
            return 0;
        return size;
    }

    // output queue
    void ikcp_qprint(String name, List<Segment> list) {
        System.out.printf("<%s>: [", name);
        for (Segment seg : list) {
            System.out.printf("(%lu %d)", (long) seg.sn, (int) (seg.ts % 10000));
            System.out.printf(",");
        }
        System.out.printf("]\n");
    }

    // can be override
    void writelog(String str, Object user) {
        System.out.println(str + ", user:" + user);
    }

    private class Segment {

        protected long conv = 0;
        protected long cmd = 0;
        protected long frg = 0;
        protected long wnd = 0;
        protected long ts = 0;
        protected long sn = 0;
        protected long una = 0;
        protected long resendts = 0;
        protected long rto = 0;
        protected long fastack = 0;
        protected long xmit = 0;
        protected byte[] data;

        protected Segment(int size) {
            this.data = new byte[size];
        }

        /**
         * ---------------------------------------------------------------------
         * ikcp_encode_seg
         * <p>
         * encode a segment into buffer
         * ---------------------------------------------------------------------
         */
        protected int encode(byte[] ptr, int offset) {
            int offset_ = offset;

            ikcp_encode32u(ptr, offset, conv);
            offset += 4;
            ikcp_encode8u(ptr, offset, (byte) cmd);
            offset += 1;
            ikcp_encode8u(ptr, offset, (byte) frg);
            offset += 1;
            ikcp_encode16u(ptr, offset, (int) wnd);
            offset += 2;
            ikcp_encode32u(ptr, offset, ts);
            offset += 4;
            ikcp_encode32u(ptr, offset, sn);
            offset += 4;
            ikcp_encode32u(ptr, offset, una);
            offset += 4;
            ikcp_encode32u(ptr, offset, (long) data.length);
            offset += 4;

            return offset - offset_;
        }
    }


    long conv, mtu, mss, state;
    long snd_una, snd_nxt, rcv_nxt;
    long ts_recent, ts_lastack, ssthresh;
    long rx_rttval, rx_srtt, rx_rto, rx_minrto;
    long snd_wnd, rcv_wnd, rmt_wnd, cwnd, probe;
    long current, interval, ts_flush, xmit;
    //    long nrcv_buf, nsnd_buf;
//    long nrcv_que, nsnd_que;
    long nodelay, updated;
    long ts_probe, probe_wait;
    long dead_link, incr;
    Deque<Segment> snd_queue;
    Deque<Segment> rcv_queue;
    Deque<Segment> snd_buf;
    Deque<Segment> rcv_buf;
    List<Long> acklist;
    //long ackcount = 0;    //用于计算 acklist 当前长度和可容纳长度的，java不需要
    //long ackblock = 0;
    public Object user;
    byte[] buffer;
    long fastresend;
    long nocwnd, stream;
    int logmask = 0;
    //long ikcp_output = NULL;
    //long writelog = NULL;

    public KCP(long conv, Object user) {
        this.conv = conv;
        this.user = user;
        this.snd_una = 0;
        this.snd_nxt = 0;
        this.rcv_nxt = 0;
        this.ts_recent = 0;
        this.ts_lastack = 0;
        this.ts_probe = 0;
        this.probe_wait = 0;
        this.snd_wnd = IKCP_WND_SND;
        this.rcv_wnd = IKCP_WND_RCV;
        this.rmt_wnd = IKCP_WND_RCV;
        this.cwnd = 0;
        this.incr = 0;
        this.probe = 0;
        this.mtu = IKCP_MTU_DEF;
        this.mss = this.mtu - IKCP_OVERHEAD;
        this.stream = 0;

        this.buffer = new byte[(int) (mtu + IKCP_OVERHEAD) * 3];

        this.snd_queue = new LinkedList<>();
        this.rcv_queue = new LinkedList<>();
        this.snd_buf = new LinkedList<>();
        this.rcv_buf = new LinkedList<>();
//        this.nrcv_buf = 0;
//        this.nsnd_buf = 0;
//        this.nrcv_que = 0;
//        this.nsnd_que = 0;
        this.state = 0;
        this.acklist = new ArrayList<>(8);
        //this.ackcount = 0;
        //this.ackblock = 0;
        this.rx_srtt = 0;
        this.rx_rttval = 0;
        this.rx_rto = IKCP_RTO_DEF;
        this.rx_minrto = IKCP_RTO_MIN;
        this.current = 0;
        this.interval = IKCP_INTERVAL;
        this.ts_flush = IKCP_INTERVAL;
        this.nodelay = 0;
        this.updated = 0;
        this.logmask = 0;
        this.ssthresh = IKCP_THRESH_INIT;
        this.fastresend = 0;
        this.nocwnd = 0;
        this.xmit = 0;
        this.dead_link = IKCP_DEADLINK;
        //this.ikcp_output = NULL;
        //this.writelog = NULL;
    }

    /**
     * ---------------------------------------------------------------------
     * user/upper level recv: returns size, returns below zero for EAGAIN
     * 将接收队列中的数据传递给上层引用
     * ---------------------------------------------------------------------
     */
    public int Recv(byte[] buffer, int len) {

        int ispeek = (len < 0) ? 1 : 0;
        if (len < 0)
            len = -len;

        if (0 == rcv_queue.size()) {
            return -1;
        }

        int peekSize = PeekSize();
        if (0 > peekSize) {
            return -2;
        }

        if (peekSize > len) {
            return -3;
        }

        boolean recover = false;
        if (rcv_queue.size() >= rcv_wnd) {
            recover = true;
        }

        // merge fragment.
        len = 0;
        for (Iterator<Segment> iter = rcv_queue.iterator(); iter.hasNext(); ) {
            Segment seg = iter.next();

            if (buffer != null) {
                System.arraycopy(seg.data, 0, buffer, len, seg.data.length);
                len += seg.data.length;
            }
            long fragment = seg.frg;

            if (ikcp_canlog(IKCP_LOG_RECV) != 0) {
                ikcp_log(IKCP_LOG_RECV, "recv sn=%lu", seg.sn);
            }

            if (ispeek == 0) {
                seg = null;
                iter.remove();
            }

            if (fragment == 0) {
                break;
            }
        }

        assert (len == peekSize);

        // move available data from rcv_buf -> rcv_queue
        for (Iterator<Segment> iter = rcv_buf.iterator(); iter.hasNext(); ) {
            Segment seg = iter.next();
            if (seg.sn == this.rcv_nxt && this.rcv_queue.size() < rcv_wnd) {
                iter.remove();
                this.rcv_queue.add(seg);
                this.rcv_nxt++;
            } else {
                break;
            }
        }

        // fast recover
        if (this.rcv_queue.size() < this.rcv_wnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe |= IKCP_ASK_TELL;
        }

        return len;
    }

    /**
     * ---------------------------------------------------------------------
     * peek data size
     * check the size of next message in the recv queue
     * 计算接收队列中有多少可用的数据
     * ---------------------------------------------------------------------
     */
    public int PeekSize() {
        Segment seg = rcv_queue.peek(); // 返回队列头部的元素  如果队列为空，则返回null
        if (seg == null) {
            return -1;
        }

        if (seg.frg == 0) {
            return seg.data.length;
        }

        if (this.rcv_queue.size() < seg.frg + 1) {
            return -1;
        }

        int length = 0;
        for (Segment item : rcv_queue) {
            length += item.data.length;
            if (item.frg == 0)
                break;
        }
        return length;
    }


    //---------------------------------------------------------------------
    // user/upper level send, returns below zero for error
    //---------------------------------------------------------------------
    // 上层要发送的数据丢给发送队列，发送队列会根据mtu大小分片
    public int Send(byte[] buffer) {
        assert (mss > 0);

        if (0 == buffer.length) {
            return -1;
        }

        int count;
        int offset = 0;
        int len = buffer.length;
        if (stream != 0) {
            if (!snd_queue.isEmpty()) {
                Segment old = snd_queue.peekLast(); //peekLast 队列为空时返回null
                if (old.data.length < this.mss) {
                    int capacity = (int) (this.mss - old.data.length);
                    int extend = (len < capacity) ? len : capacity;
                    Segment seg = new Segment(old.data.length + extend);    //seg->len = old->len + extend;
                    assert (seg != null);
//                    if (seg == null) {
//                        return -2;
//                    }
                    System.arraycopy(old.data, 0, seg.data, 0, old.data.length);

                    if (buffer != null) {
                        System.arraycopy(buffer, 0, seg.data, old.data.length, extend);
                        offset += extend;
                    }
                    seg.frg = 0;
                    len -= extend;
                    snd_queue.pollLast();//弹出队列尾部元素,队列为空时返回null
                    snd_queue.add(seg);
                }
            }
            if (len <= 0)
                return 0;
        }

        // 根据mss大小分片
        if (len <= mss) {
            count = 1;
        } else {
            count = (int) (len + mss - 1) / (int) mss;
        }

        if (255 < count) {
            return -2;
        }

        if (0 == count) {
            count = 1;
        }

        offset = 0;

        // 分片后加入到发送队列
        int length = buffer.length;
        for (int i = 0; i < count; i++) {
            int size = (int) (length > mss ? mss : length);
            Segment seg = new Segment(size);
            if (buffer != null && len > 0)
                System.arraycopy(buffer, offset, seg.data, 0, size);
            seg.frg = (this.stream == 0) ? (count - i - 1) : 0;
            snd_queue.add(seg);
            offset += size;
            length -= size;
        }

        return 0;
    }


    //---------------------------------------------------------------------
    // parse ack
    //---------------------------------------------------------------------
    void update_ack(int rtt) {
        if (0 == rx_srtt) {
            rx_srtt = rtt;
            rx_rttval = rtt / 2;
        } else {
            int delta = (int) (rtt - rx_srtt);
            if (0 > delta) {
                delta = -delta;
            }

            rx_rttval = (3 * rx_rttval + delta) / 4;
            rx_srtt = (7 * rx_srtt + rtt) / 8;
            if (rx_srtt < 1) {
                rx_srtt = 1;
            }
        }

        int rto = (int) (rx_srtt + _imax_(1, 4 * rx_rttval));
        rx_rto = _ibound_(rx_minrto, rto, IKCP_RTO_MAX);
    }

    // 计算本地真实snd_una
    void shrink_buf() {
        Segment seg = snd_buf.peekFirst();
        if (seg != null) {
            snd_una = seg.sn;
        } else {
            snd_una = snd_nxt;
        }
    }

    // 对端返回的ack, 确认发送成功时，对应包从发送缓存中移除
    void parse_ack(long sn) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return;
        }

        int index = 0;
        for (Segment seg : snd_buf) {
            if (sn == seg.sn) {
                snd_buf.remove(index);
                break;
            }
            index++;
            if (_itimediff(sn, seg.sn) < 0) {
                break;
            }
        }
    }

    // 通过对端传回的una将已经确认发送成功包从发送缓存中移除
    void parse_una(long una) {
        for (Iterator<Segment> iter = snd_buf.iterator(); iter.hasNext(); ) {
            Segment seg = iter.next();
            if (_itimediff(una, seg.sn) > 0) {
                iter.remove();
            } else {
                break;
            }
        }
    }

    void parse_fastack(long sn) {
        if (_itimediff(sn, snd_una) < 0 || _itimediff(sn, snd_nxt) >= 0) {
            return;
        }

        for (Segment seg : snd_buf) {
            if (_itimediff(sn, seg.sn) < 0) {
                break;
            } else if (sn != seg.sn) {
                seg.fastack++;
            }
        }
    }

    //---------------------------------------------------------------------
    // ack append
    //---------------------------------------------------------------------
    // 收数据包后需要给对端回ack，flush时发送出去
    void ack_push(long sn, long ts) {
        // c原版实现中数组按*2扩大容量，arrayList自动扩容，不需要考虑这个
        acklist.add(sn);
        acklist.add(ts);
    }

    /**
     * ---------------------------------------------------------------------
     * parse data
     * <p>
     * 用户数据包解析
     * ---------------------------------------------------------------------
     */
    void parse_data(Segment newseg) {
        long sn = newseg.sn;
        boolean repeat = false;

        if (_itimediff(sn, rcv_nxt + rcv_wnd) >= 0 || _itimediff(sn, rcv_nxt) < 0) {
            return;
        }

        int i = rcv_buf.size() - 1; // 初始指向最后一个元素的下标位置
        // 判断是否是重复包，并且计算插入位置
        for (Iterator<Segment> iter = rcv_buf.descendingIterator(); iter.hasNext(); i--) {
            Segment seg = iter.next();
            if (seg.sn == sn) {
                repeat = true;
                break;
            }

            if (_itimediff(sn, seg.sn) > 0) {
                break;
            }
        }

        // 如果不是重复包，则插入
        if (!repeat) {
            //iqueue_add(&newseg->node, p);   //原版C代码，newseg添加在p后面。
            // 这里下标i 就是p的位置，要插入p的后面，则下标应该为 i+1
            ((LinkedList<Segment>) rcv_buf).add(i + 1, newseg);
        }

        // move available data from rcv_buf -> rcv_queue
        // 将连续包加入到接收队列
        for (Iterator<Segment> iter = rcv_buf.iterator(); iter.hasNext(); ) {
            Segment seg = iter.next();
            if (seg.sn == rcv_nxt && rcv_queue.size() < rcv_wnd) {
                iter.remove();// 从接收缓存中移除
                this.rcv_queue.add(seg);
                this.rcv_nxt++;
            } else {
                break;
            }
        }
    }

    // when you received a low level packet (eg. UDP packet), call it
    //---------------------------------------------------------------------
    // input data
    //---------------------------------------------------------------------
    // 底层收包后调用，再由上层通过Recv获得处理后的数据
    public int Input(byte[] data, int size) {

        boolean flag = false;
        long maxack = 0;

        if (ikcp_canlog(IKCP_LOG_INPUT) != 0) {
            ikcp_log(IKCP_LOG_INPUT, "[RI] %d bytes", size);
        }

        long s_una = snd_una;
        if (data == null || size < IKCP_OVERHEAD) {
            return -1;
        }

        int offset = 0;

        while (true) {
            long ts, sn, length, una, conv_;
            int wnd;
            byte cmd, frg;

            if (size - offset < IKCP_OVERHEAD) {
                break;
            }

            conv_ = ikcp_decode32u(data, offset);
            offset += 4;
            if (conv != conv_) {
                return -1;
            }

            cmd = ikcp_decode8u(data, offset);
            offset += 1;
            frg = ikcp_decode8u(data, offset);
            offset += 1;
            wnd = ikcp_decode16u(data, offset);
            offset += 2;
            ts = ikcp_decode32u(data, offset);
            offset += 4;
            sn = ikcp_decode32u(data, offset);
            offset += 4;
            una = ikcp_decode32u(data, offset);
            offset += 4;
            length = ikcp_decode32u(data, offset);
            offset += 4;

            if (data.length - offset < length) {
                return -2;
            }

            if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
                return -3;
            }

            rmt_wnd = (long) wnd;
            parse_una(una);
            shrink_buf();

            if (IKCP_CMD_ACK == cmd) {
                if (_itimediff(current, ts) >= 0) {
                    update_ack(_itimediff(current, ts));
                }
                parse_ack(sn);
                shrink_buf();
                if (flag == false) {
                    flag = true;
                    maxack = sn;
                } else {
                    if (_itimediff(sn, maxack) > 0) {
                        maxack = sn;
                    }
                }
                if (ikcp_canlog(IKCP_LOG_IN_ACK) != 0) {
                    ikcp_log(IKCP_LOG_IN_DATA,
                            "input ack: sn=%lu rtt=%ld rto=%ld", sn,
                            (long) _itimediff(this.current, ts),
                            (long) this.rx_rto);
                }
            } else if (IKCP_CMD_PUSH == cmd) {
                if (ikcp_canlog(IKCP_LOG_IN_DATA) != 0) {
                    ikcp_log(IKCP_LOG_IN_DATA, "input psh: sn=%lu ts=%lu", sn, ts);
                }
                if (_itimediff(sn, rcv_nxt + rcv_wnd) < 0) {
                    ack_push(sn, ts);
                    if (_itimediff(sn, rcv_nxt) >= 0) {
                        Segment seg = new Segment((int) length);
                        seg.conv = conv_;
                        seg.cmd = cmd;
                        seg.frg = frg;
                        seg.wnd = wnd;
                        seg.ts = ts;
                        seg.sn = sn;
                        seg.una = una;

                        if (length > 0) {
                            System.arraycopy(data, offset, seg.data, 0, (int) length);
                        }

                        parse_data(seg);
                    }
                }
            } else if (IKCP_CMD_WASK == cmd) {
                // ready to send back IKCP_CMD_WINS in Ikcp_flush
                // tell remote my window size
                probe |= IKCP_ASK_TELL;
                if (ikcp_canlog(IKCP_LOG_IN_PROBE) != 0) {
                    ikcp_log(IKCP_LOG_IN_PROBE, "input probe");
                }
            } else if (IKCP_CMD_WINS == cmd) {
                // do nothing
                if (ikcp_canlog(IKCP_LOG_IN_WINS) != 0) {
                    ikcp_log(IKCP_LOG_IN_WINS, "input wins: %lu", (long) (wnd));
                }
            } else {
                return -3;
            }

            offset += (int) length;
        }
        if (flag == true) {
            parse_fastack(maxack);
        }

        if (_itimediff(snd_una, s_una) > 0) {
            if (cwnd < rmt_wnd) {
                long mss_ = mss;
                if (cwnd < ssthresh) {
                    cwnd++;
                    incr += mss_;
                } else {
                    if (incr < mss_) {
                        incr = mss_;
                    }
                    incr += (mss_ * mss_) / incr + (mss_ / 16);
                    if ((cwnd + 1) * mss_ <= incr) {
                        cwnd++;
                    }
                }
                if (cwnd > rmt_wnd) {
                    cwnd = rmt_wnd;
                    incr = rmt_wnd * mss_;
                }
            }
        }

        return 0;
    }


    // 接收窗口可用大小
    int wnd_unused() {
        if (rcv_queue.size() < rcv_wnd) {
            return (int) (int) rcv_wnd - rcv_queue.size();
        }
        return 0;
    }

    void flush() {//viewed
        long current_ = current;
        int change = 0;
        int lost = 0;

        // 'ikcp_update' haven't been called.
        if (0 == updated) {
            return;
        }

        Segment seg = new Segment(0);
        seg.conv = conv;
        seg.cmd = IKCP_CMD_ACK;
        seg.wnd = (long) wnd_unused();
        seg.una = rcv_nxt;

        // flush acknowledges
        // 将acklist中的ack发送出去
        int count = acklist.size() / 2;
        int size = 0;
        for (int i = 0; i < count; i++) {
            if (size + IKCP_OVERHEAD > mtu) {
                ikcp_output(buffer, size);
                size = 0;
            }
            // ikcp_ack_get
            seg.sn = acklist.get(i * 2 + 0);
            seg.ts = acklist.get(i * 2 + 1);
            size += seg.encode(buffer, size);
        }
        acklist.clear();

        // probe window size (if remote window size equals zero)
        // rmt_wnd=0时，判断是否需要请求对端接收窗口
        if (0 == rmt_wnd) {
            if (0 == probe_wait) {
                probe_wait = IKCP_PROBE_INIT;
                ts_probe = current + probe_wait;
            } else {
                // 逐步扩大请求时间间隔
                if (_itimediff(current, ts_probe) >= 0) {
                    if (probe_wait < IKCP_PROBE_INIT) {
                        probe_wait = IKCP_PROBE_INIT;
                    }
                    probe_wait += probe_wait / 2;
                    if (probe_wait > IKCP_PROBE_LIMIT) {
                        probe_wait = IKCP_PROBE_LIMIT;
                    }
                    ts_probe = current + probe_wait;
                    probe |= IKCP_ASK_SEND;
                }
            }
        } else {
            ts_probe = 0;
            probe_wait = 0;
        }

        // flush window probing commands
        // 请求对端接收窗口
        if ((probe & IKCP_ASK_SEND) != 0) {
            seg.cmd = IKCP_CMD_WASK;
            if (size + IKCP_OVERHEAD > mtu) {
                ikcp_output(buffer, size);
                size = 0;
            }
            size += seg.encode(buffer, size);
        }

        // flush window probing commands(c#)
        // 告诉对端自己的接收窗口
        if ((probe & IKCP_ASK_TELL) != 0) {
            seg.cmd = IKCP_CMD_WINS;
            if (size + IKCP_OVERHEAD > mtu) {
                ikcp_output(buffer, size);
                size = 0;
            }
            size += seg.encode(buffer, size);
        }

        this.probe = 0;

        // calculate window size
        long cwnd_ = _imin_(snd_wnd, rmt_wnd);
        // 如果采用拥塞控制
        if (0 == nocwnd) {
            cwnd_ = _imin_(cwnd, cwnd_);
        }

        count = 0;
        // move data from snd_queue to snd_buf
        while (_itimediff(snd_nxt, snd_una + cwnd_) < 0) {
            Segment newseg = snd_queue.poll();
            if (newseg != null) {
                newseg.conv = this.conv;
                newseg.cmd = IKCP_CMD_PUSH;
                newseg.wnd = seg.wnd;
                newseg.ts = current_;
                newseg.sn = snd_nxt;
                newseg.una = rcv_nxt;
                newseg.resendts = current_;
                newseg.rto = this.rx_rto;
                newseg.fastack = 0;
                newseg.xmit = 0;
                snd_buf.add(newseg);
                snd_nxt++;
            }
        }

        // calculate resent
        long resent = (fastresend > 0) ? fastresend : 0xffffffff;
        long rtomin = (nodelay == 0) ? (rx_rto >> 3) : 0;

        // flush data segments
        for (Segment segment : snd_buf) {
            boolean needsend = false;
            if (0 == segment.xmit) {
                // 第一次传输
                needsend = true;
                segment.xmit++;
                segment.rto = rx_rto;
                segment.resendts = current_ + segment.rto + rtomin;
            } else if (_itimediff(current_, segment.resendts) >= 0) {
                // 丢包重传
                needsend = true;
                segment.xmit++;
                xmit++;
                if (0 == nodelay) {
                    segment.rto += rx_rto;
                } else {
                    segment.rto += rx_rto / 2;
                }
                segment.resendts = current_ + segment.rto;
                lost = 1;
            } else if (segment.fastack >= resent) {
                // 快速重传
                needsend = true;
                segment.xmit++;
                segment.fastack = 0;
                segment.resendts = current_ + segment.rto;
                change++;
            }

            if (needsend) {
                segment.ts = current_;
                segment.wnd = seg.wnd;
                segment.una = rcv_nxt;

                int need = IKCP_OVERHEAD + segment.data.length;
                if (size + need >= mtu) {
                    ikcp_output(buffer, size);
                    size = 0;
                }

                size += segment.encode(buffer, size);
                if (segment.data.length > 0) {
                    System.arraycopy(segment.data, 0, buffer, size, segment.data.length);
                    size += segment.data.length;
                }

                if (segment.xmit >= dead_link) {
                    state = -1; // state = 0(c#)
                }
            }
        }

        // flash remain segments
        if (size > 0) {
            ikcp_output(buffer, size);
        }

        // update ssthresh
        // 拥塞避免
        if (change != 0) {
            long inflight = snd_nxt - snd_una;
            ssthresh = inflight / 2;
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN;
            }
            cwnd = ssthresh + resent;
            incr = cwnd * mss;
        }

        if (lost != 0) {
            ssthresh = cwnd / 2;
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN;
            }
            cwnd = 1;
            incr = mss;
        }

        if (cwnd < 1) {
            cwnd = 1;
            incr = mss;
        }
    }

    //---------------------------------------------------------------------
    // update state (call it repeatedly, every 10ms-100ms), or you can ask
    // ikcp_check when to call it again (without ikcp_input/_send calling).
    // 'current' - current timestamp in millisec.
    //---------------------------------------------------------------------
    public void Update(long current_) {//viewed

        current = current_;

        // 首次调用Update
        if (0 == updated) {
            updated = 1;
            ts_flush = current;
        }

        // 两次更新间隔
        int slap = _itimediff(current, ts_flush);

        // interval设置过大或者Update调用间隔太久
        if (slap >= 10000 || slap < -10000) {
            ts_flush = current;
            slap = 0;
        }

        // flush同时设置下一次更新时间
        if (slap >= 0) {
            ts_flush += interval;
            if (_itimediff(current, ts_flush) >= 0) {
                ts_flush = current + interval;
            }
            flush();
        }
    }


    //---------------------------------------------------------------------
    // Determine when should you invoke ikcp_update:
    // returns when you should invoke ikcp_update in millisec, if there
    // is no ikcp_input/_send calling. you can call ikcp_update in that
    // time, instead of call update repeatly.
    // Important to reduce unnacessary ikcp_update invoking. use it to
    // schedule ikcp_update (eg. implementing an epoll-like mechanism,
    // or optimize ikcp_update when handling massive kcp connections)
    //---------------------------------------------------------------------
    long Check(long current_) {
        long ts_flush_ = ts_flush;
        long tm_flush = 0x7fffffff;
        long tm_packet = 0x7fffffff;
        long minimal;

        if (0 == updated) {
            return current_;
        }

        if (_itimediff(current_, ts_flush_) >= 10000 || _itimediff(current_, ts_flush_) < -10000) {
            ts_flush_ = current_;
        }

        if (_itimediff(current_, ts_flush_) >= 0) {
            return current_;
        }

        tm_flush = _itimediff(ts_flush_, current_);

        for (Segment seg : snd_buf) {
            int diff = _itimediff(seg.resendts, current_);
            if (diff <= 0) {
                return current_;
            }
            if (diff < tm_packet) {
                tm_packet = diff;
            }
        }

        minimal = tm_packet < tm_flush ? tm_packet : tm_flush;
        if (minimal >= interval) {
            minimal = interval;
        }

        return current_ + minimal;
    }


    // change MTU size, default is 1400
    int SetMtu(int mtu_) {
        if (mtu_ < 50 || mtu_ < IKCP_OVERHEAD)
            return -1;
        byte[] buffer_ = new byte[(int) (mtu_ + IKCP_OVERHEAD) * 3];
//        if (buffer_ == null)
//            return -2;
        this.mtu = (long) mtu_;
        mss = mtu_ - IKCP_OVERHEAD;
        this.buffer = buffer_;
        return 0;
    }

    int Interval(int interval) {
        if (interval > 5000) interval = 5000;
        else if (interval < 10) interval = 10;
        this.interval = interval;
        return 0;
    }

    // fastest: ikcp_nodelay(kcp, 1, 20, 2, 1)
    // nodelay: 0:disable(default), 1:enable
    // interval: internal update timer interval in millisec, default is 100ms
    // resend: 0:disable fast resend(default), 1:enable fast resend
    // nc: 0:normal congestion control(default), 1:disable congestion control
    int NoDelay(int nodelay, int interval, int resend, int nc) {
        if (nodelay >= 0) {
            this.nodelay = nodelay;
            if (nodelay != 0) {
                this.rx_minrto = IKCP_RTO_NDL;
            } else {
                this.rx_minrto = IKCP_RTO_MIN;
            }
        }
        if (interval >= 0) {
            if (interval > 5000) interval = 5000;
            else if (interval < 10) interval = 10;
            this.interval = interval;
        }
        if (resend >= 0) {
            this.fastresend = resend;
        }
        if (nc >= 0) {
            this.nocwnd = nc;
        }
        return 0;
    }

    // set maximum window size: sndwnd=32, rcvwnd=32 by default
    int WndSize(int sndwnd, int rcvwnd) {
        if (sndwnd > 0) {
            this.snd_wnd = sndwnd;
        }
        if (rcvwnd > 0) {   // must >= max fragment size
            this.rcv_wnd = _imax_(rcvwnd, IKCP_WND_RCV);
        }
        return 0;
    }

    // get how many packet is waiting to be sent
    int WaitSnd() {
        return this.snd_buf.size() + this.snd_queue.size();
    }


    // read conv
    long ikcp_getconv(byte[] data, int offset) {
        long conv_ = ikcp_decode32u(data, offset);
        return conv_;
    }


}