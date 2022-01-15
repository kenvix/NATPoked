//--------------------------------------------------
// Class StunKit
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network;

import com.kenvix.natpoked.contacts.NATType;
import com.kenvix.natpoked.utils.AppEnv;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;

final class StunKit {
    private static final int RECEIVE_TIMEOUT = AppEnv.INSTANCE.getStunQueryTimeout();
    private final InetAddress stunServerAddr;
    private final int stunServerPort;

    public StunKit(InetAddress stunServerAddr, int stunServerPort) {
        this.stunServerAddr = stunServerAddr;
        this.stunServerPort = stunServerPort;
    }


    public static class StunResult {
        public NATType natType;
        public String publicIp;
        public int publicPort;

        @Override
        public String toString() {
            return "natType:\t" + natType +
                    "\npublicIp:\t" + publicIp +
                    "\npublicPort:\t" + publicPort;
        }
    }

    public StunResult makeStun(DatagramSocket socket) {
        if (!socket.isBound()) throw new RuntimeException("can not process a unbound datagram socket");
        StunResult result = null;
        int oldReceiveTimeout = 0;
        try {
            oldReceiveTimeout = socket.getSoTimeout();
            socket.setSoTimeout(RECEIVE_TIMEOUT);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        //do test1
        ResponseResult stunResponse = stunTest(socket, null);
        //System.out.println(stunResponse);

        //NOTE:
        //It is no need to understand the nat type for my desire,
        //so ignore subsequent stun test

        try {
            socket.setSoTimeout(oldReceiveTimeout);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        if (stunResponse != null && stunResponse.responsed) {
            result = new StunResult();
            result.natType = NATType.UNKNOWN;
            result.publicIp = stunResponse.externalIp;
            result.publicPort = stunResponse.externalPort;
        }

        return result;
    }

    private static class ResponseResult {
        public boolean responsed;
        public String externalIp;
        public int externalPort;
        public String sourceIp;
        public int sourcePort;
        public String changedIp;
        public int changedPort;

        @Override
        public String toString() {
            return "responsed:\t" + responsed +
                    "\nexternalIp:\t" + externalIp +
                    "\nexternalPort:\t" + externalPort +
                    "\nsourceIp:\t" + sourceIp +
                    "\nsourcePort:\t" + sourcePort +
                    "\nchangedIp:\t" + changedIp +
                    "\nchangedPort:\t" + changedPort;
        }
    }

    private ResponseResult stunTest(DatagramSocket socket, byte[] msgData) {
        ResponseResult result = new ResponseResult();
        int msgLength = msgData == null ? 0 : msgData.length;
        MessageHeader bindRequestHeader = new MessageHeader();
        bindRequestHeader.generateTransactionID();
        bindRequestHeader.setMessageLength(msgLength);
        bindRequestHeader.setStunType(MessageHeader.StunType.BIND_REQUEST_MSG);
        byte[] headerData = bindRequestHeader.encode();
        byte[] sendData = new byte[headerData.length + msgLength];
        System.arraycopy(headerData, 0, sendData, 0, headerData.length);
        if (msgLength > 0) System.arraycopy(msgData, 0, sendData, headerData.length, msgLength);

        int tryForGettingCorrectPacketCount = 3;
        while (tryForGettingCorrectPacketCount > 0) {
            int tryForGettingDataCount = 3;
            byte[] receivedData = null;
            //System.out.println("###############################################################");
            while (receivedData == null) {
                try {
                    DatagramPacket sendPacket = new DatagramPacket(
                            sendData,
                            sendData.length,
                            stunServerAddr, stunServerPort);
                    socket.send(sendPacket);

                    DatagramPacket receivePacket = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(receivePacket);

                    receivedData = Arrays.copyOfRange(receivePacket.getData(), 0, receivePacket.getLength());
                    //System.out.println("got data! -------------------------------------------------------##" + receivedData.length);
                } catch (Exception e) {
                    e.printStackTrace();
                    //System.out.println("tryForGettingDataCount is : " + tryForGettingDataCount);
                    if (tryForGettingDataCount > 0) {
                        tryForGettingDataCount--;
                    } else {
                        result.responsed = false;
                        return result;
                    }
                }
            }

            Message receivedMessage = Message.parseData(receivedData);
            if (receivedMessage != null &&
                    receivedMessage.getStunType() == MessageHeader.StunType.BIND_RESPONSE_MSG &&
                    Arrays.equals(receivedMessage.getTransactionId(), bindRequestHeader.getTransactionId())) {
                MessageAttribute[] attributes = receivedMessage.getAttributes();
                //System.out.println("message data was received , attributes list below:");
                result.responsed = true;
                for (MessageAttribute attr : attributes) {
                    //System.out.println(attr.toString());
                    if (attr instanceof MappedAddress) {
                        MappedAddress ma = (MappedAddress) attr;
                        result.externalIp = ma.getAddress();
                        result.externalPort = ma.getPort();
                    } else if (attr instanceof SourceAddress) {
                        SourceAddress sa = (SourceAddress) attr;
                        result.sourceIp = sa.getAddress();
                        result.sourcePort = sa.getPort();
                    } else if (attr instanceof ChangedAddress) {
                        ChangedAddress ca = (ChangedAddress) attr;
                        result.changedIp = ca.getAddress();
                        result.changedPort = ca.getPort();
                    }
                }
                return result;
            }

            tryForGettingCorrectPacketCount--;
        }
        return null;
    }

    private static class UtilityException extends Exception {
        //private static final long serialVersionUID = 3545800974716581680L;
        UtilityException(String mesg) {
            super(mesg);
        }
    }

    private static class Message {
        private MessageHeader header;
        /*
        public MessageHeader getHeader() {
            return header;
        }
        */
        private MessageAttribute[] attributes;

        public MessageAttribute[] getAttributes() {
            return attributes;
        }


        public MessageHeader.StunType getStunType() {
            if (header == null) return null;
            return header.getStunType();
        }

        public byte[] getTransactionId() {
            if (header == null) return null;
            return header.getTransactionId();
        }


        public static Message parseData(byte[] messageData) {
            try {
                MessageHeader header = new MessageHeader();

                int msgLength = Utility.twoBytesToInteger(messageData, 2);
                if (messageData.length != msgLength + MessageHeader.HEAD_LENGTH) return null;
                header.setMessageLength(msgLength);

                int stunType = Utility.twoBytesToInteger(messageData, 0);
                MessageHeader.StunType[] types = MessageHeader.StunType.values();
                for (MessageHeader.StunType type : types) {
                    if (type.getValue() == stunType) {
                        header.setStunType(type);
                        break;
                    }
                }
                if (header.getStunType() == null) return null;

                byte[] tranId = new byte[16];
                System.arraycopy(messageData, 4, tranId, 0, 16);
                header.setTransactionId(tranId);

                //MessageHead parsing is finished
                MessageAttribute[] attributes = MessageAttribute.parseData(messageData);
                if (attributes != null && attributes.length > 0) {
                    Message msg = new Message();
                    msg.header = header;
                    msg.attributes = attributes;
                    return msg;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /*
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |         Type                  |            Length             |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                             Value                             ....
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * */
    private static abstract class MessageAttribute {
        private static final int MAPPED_ADDRESS = 0x0001;
        private static final int RESPONSE_ADDRESS = 0X0002;
        private static final int CHANGE_REQUEST = 0X0003;
        private static final int SOURCE_ADDRESS = 0X0004;
        private static final int CHANGED_ADDRESS = 0X0005;

        public static MessageAttribute[] parseData(byte[] messageData) {
            try {
                ArrayList<MessageAttribute> attributeList = new ArrayList<MessageAttribute>();
                int offset = MessageHeader.HEAD_LENGTH;
                //int lengthRemain =  Utility.twoBytesToInteger(messageData, 2);

                while (offset < messageData.length) {
                    int attrType = Utility.twoBytesToInteger(messageData, offset);
                    int attrLength = Utility.twoBytesToInteger(messageData, offset + 2);

                    MessageAttribute attr = null;
                    switch (attrType) {
                        case MAPPED_ADDRESS:
                            attr = new MappedAddress();
                            break;
                        case SOURCE_ADDRESS:
                            attr = new SourceAddress();
                            break;
                        case CHANGED_ADDRESS:
                            attr = new ChangedAddress();
                            break;
                        default:
                            attr = new UnknownAttribute(attrType, attrLength);
                    }
                    if (messageData.length >= attr.getLength() + offset + 4) {
                        attr.parse(messageData, offset + 4);
                        attributeList.add(attr);
                    } else {
                        //messageData is incorrect
                        throw new Exception("messageData is incorrect");
                    }
                    offset += attrLength + 4;
                }

                int size = attributeList.size();
                if (size > 0) {
                    MessageAttribute[] attrs = new MessageAttribute[size];
                    return attributeList.toArray(attrs);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        public abstract int getTypeCode();

        public abstract int getLength();

        public abstract void parse(byte[] messageData, int offset);

        public abstract String toString();
    }

    /*
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *|x x x x x x x x|    Family     |           Port                |
     *+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *|                             Address                           |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private static abstract class AddressAttribute extends MessageAttribute {

        @Override
        public int getLength() {
            return 8;
        }

        @Override
        public String toString() {
            return "type:" + getTypeCode() +
                    "\tlength:" + getLength() +
                    "\tip:" + mAddress +
                    "\tport:" + mPort;
        }

        @Override
        public void parse(byte[] messageData, int offset) {
            try {
                mPort = Utility.twoBytesToInteger(messageData, offset + 2);
                StringBuilder sb = new StringBuilder(15);
                sb.append(Utility.oneByteToInteger(messageData, offset + 4)).
                        append(".").
                        append(Utility.oneByteToInteger(messageData, offset + 5)).
                        append(".").
                        append(Utility.oneByteToInteger(messageData, offset + 6)).
                        append(".").
                        append(Utility.oneByteToInteger(messageData, offset + 7));
                mAddress = sb.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private int mPort;

        public int getPort() {
            return mPort;
        }

        private String mAddress;

        public String getAddress() {
            return mAddress;
        }
    }

    private static class MappedAddress extends AddressAttribute {
        @Override
        public int getTypeCode() {
            return 0x0001;
        }
    }

    /* ignore all attributes present in request
    private static class ResponseAddress extends AddressAttribute {
        @Override
        public int getTypeCode() { return 0x0002; }
    }
    */

    private static class SourceAddress extends AddressAttribute {
        @Override
        public int getTypeCode() {
            return 0x0004;
        }
    }

    private static class ChangedAddress extends AddressAttribute {
        @Override
        public int getTypeCode() {
            return 0x0005;
        }
    }

    /*
     * 0                   1                   2                   3
     * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 A B 0|
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    /*
    private static class ChangeRequest extends MessageAttribute {
       @Override
       public int getTypeCode() { return 0x0003; }

       @Override
       public int getLength() { return 4; }

       boolean mChangeIp;
       public boolean isChangeIp() {return mChangeIp;}
       public void setChangeIp(boolean changeIp) { mChangeIp = changeIp; }
       boolean mChangePort;
       public boolean isChangePort() {return mChangePort;}
       public void setChangePort(boolean changePort) { mChangePort = changePort; }
    }
    */

    private static class UnknownAttribute extends MessageAttribute {
        int mTypeCode;
        int mLength;
        byte[] mValue;

        public UnknownAttribute(int typeCode, int length) {
            mTypeCode = typeCode;
            mLength = length;
        }

        @Override
        public int getTypeCode() {
            return mTypeCode;
        }

        @Override
        public int getLength() {
            return mLength;
        }

        @Override
        public void parse(byte[] messageData, int offset) {
            mValue = new byte[mLength];
            System.arraycopy(messageData, offset, mValue, 0, mLength);
        }

        @Override
        public String toString() {
            return "UnknownAttribute\t" +
                    "type:" + mTypeCode +
                    "\tlength:" + mLength;
        }
    }

    /*
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |      STUN Message Type        |         Message Length        |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *                          Transaction ID
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *                                                                 |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private static class MessageHeader {
        public static final int HEAD_LENGTH = 20;
        private byte[] mStunType;// = new byte[2];
        private byte[] mMessageLength;// = new byte[2];
        private byte[] mTranId;// = new byte[16];

        public enum StunType {
            BIND_REQUEST_MSG(0x0001),
            BIND_RESPONSE_MSG(0X0101),
            BIND_ERROR_RESPONSE_MSG(0x0111),
            SHARED_SECRET_REQUEST_MSG(0x0002),
            SHARED_SECRET_RESPONSE_MSG(0X0102),
            SHARED_SECRETERROR_RESPONSE_MSG(0x0112);
            private final int value;

            private StunType(int value) {
                this.value = value;
            }

            public String toString() {
                return super.toString() + "value:" + value;
            }

            public int getValue() {
                return value;
            }
        }

        public StunType getStunType() {
            if (mStunType == null) return null;
            try {
                int intType = Utility.twoBytesToInteger(mStunType);
                StunType[] types = StunType.values();
                for (StunType type : types) {
                    if (type.getValue() == intType) {
                        return type;
                    }
                }
            } catch (UtilityException e) {
                e.printStackTrace();
            }
            return null;
        }

        public void setStunType(StunType type) {
            try {
                mStunType = Utility.integerToTwoBytes(type.getValue());
            } catch (UtilityException e) {
                e.printStackTrace();
            }
        }

        public int getMessageLength() {
            try {
                return Utility.twoBytesToInteger(mMessageLength);
            } catch (UtilityException e) {
                e.printStackTrace();
            }
            return -1;
        }

        public void setMessageLength(int length) {
            try {
                mMessageLength = Utility.integerToTwoBytes(length);
            } catch (UtilityException e) {
                e.printStackTrace();
            }
        }

        public void generateTransactionID() {
            mTranId = new byte[16];
            try {
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 0, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 2, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 4, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 6, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 8, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 10, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 12, 2);
                System.arraycopy(Utility.integerToTwoBytes((int) (Math.random() * 65536)), 0, mTranId, 14, 2);
            } catch (UtilityException e) {
                e.printStackTrace();
            }
        }

        public byte[] getTransactionId() {
            return mTranId;
        }

        public boolean setTransactionId(byte[] tranId) {
            if (tranId.length != 16) return false;
            mTranId = tranId;
            return true;
        }

        public byte[] encode() {
            if (mStunType == null || mStunType.length != 2) throw new RuntimeException("Stuntype is not correct");
            if (mMessageLength == null || mMessageLength.length != 2)
                throw new RuntimeException("mMessageLength is not correct");
            if (mTranId == null || mTranId.length != 16) throw new RuntimeException(" mTranId is not correct");

            byte[] result = new byte[HEAD_LENGTH];
            System.arraycopy(mStunType, 0, result, 0, 2);
            System.arraycopy(mMessageLength, 0, result, 2, 2);
            System.arraycopy(mTranId, 0, result, 4, 16);
            return result;
        }
    }

    private static class Utility {
        public static final byte integerToOneByte(int value) throws UtilityException {
            if ((value > Math.pow(2, 15)) || (value < 0)) {
                throw new UtilityException("Integer value " + value + " is larger than 2^15");
            }
            return (byte) (value & 0xFF);
        }

        public static final byte[] integerToTwoBytes(int value) throws UtilityException {
            byte[] result = new byte[2];
            if ((value > Math.pow(2, 31)) || (value < 0)) {
                throw new UtilityException("Integer value " + value + " is larger than 2^31");
            }
            result[0] = (byte) ((value >>> 8) & 0xFF);
            result[1] = (byte) (value & 0xFF);
            return result;
        }

        public static final byte[] integerToFourBytes(int value) throws UtilityException {
            byte[] result = new byte[4];
            if ((value > Math.pow(2, 63)) || (value < 0)) {
                throw new UtilityException("Integer value " + value + " is larger than 2^63");
            }
            result[0] = (byte) ((value >>> 24) & 0xFF);
            result[1] = (byte) ((value >>> 16) & 0xFF);
            result[2] = (byte) ((value >>> 8) & 0xFF);
            result[3] = (byte) (value & 0xFF);
            return result;
        }

        public static final int oneByteToInteger(byte value) throws UtilityException {
            byte[] val = new byte[1];
            val[0] = value;
            return oneByteToInteger(val, 0);
        }

        public static final int oneByteToInteger(byte[] value, int offset) throws UtilityException {
            if (value.length < 1 + offset) {
                throw new UtilityException("Byte array too short!");
            }

            return (int) value[offset] & 0xFF;
        }

        public static final int twoBytesToInteger(byte[] value) throws UtilityException {
            return twoBytesToInteger(value, 0);
        }

        public static final int twoBytesToInteger(byte[] value, int offset) throws UtilityException {
            if (value.length < 2 + offset) {
                throw new UtilityException("Byte array too short!");
            }
            int temp0 = value[offset] & 0xFF;
            int temp1 = value[1 + offset] & 0xFF;
            return ((temp0 << 8) + temp1);
        }

        public static final long fourBytesToLong(byte[] value) throws UtilityException {
            return fourBytesToLong(value, 0);
        }

        public static final long fourBytesToLong(byte[] value, int offset) throws UtilityException {
            if (value.length < 4 + offset) {
                throw new UtilityException("Byte array too short!");
            }
            int temp0 = value[offset] & 0xFF;
            int temp1 = value[1 + offset] & 0xFF;
            int temp2 = value[2 + offset] & 0xFF;
            int temp3 = value[3 + offset] & 0xFF;
            return (((long) temp0 << 24) + (temp1 << 16) + (temp2 << 8) + temp3);
        }
    }

    /*
    public static void main(String[] args) {
        System.out.println("aaa");
        DatagramSocket socket = null;
        try{
            int port = Integer.parseInt(args[0]);
            socket = new DatagramSocket(port);
            StunResult stunResult = makeStun(socket);
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            System.out.println(stunResult);
            socket.close();
        } catch (Exception e ) {
            e.printStackTrace();
        }
    }
    */
}