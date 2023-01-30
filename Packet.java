import java.nio.ByteBuffer;
// @Jukka J
// 25.01.2023

public class Packet {
    private static final int maxSize = 512;
    private static final int headerSize = 12;

    private int seqNum;
    private byte[] data;
    private int type;
    private int length;

    Packet(int type, int length, int seqNum, byte[] data) throws Exception {
        if (length > maxSize) {
            throw new Exception("Packet size exceed maximum size allowed (512)");
        }
        this.type = type;
        this.length = length;
        this.seqNum = seqNum;
        this.data = data;
    }

    public static Packet createACK(int seqNum) throws Exception {
        return new Packet(1, headerSize, seqNum, new byte[0]);
    }

    public static Packet createEOT(int seqNum) throws Exception {
        return new Packet(2, headerSize, seqNum, new byte[0]);
    }
    // Getters
    public int getType() {
        return type;
    }

    public int getLength() {
        return length;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public byte[] getData() {
        return data;
    }

    public byte[] getBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.putInt(type);
        buffer.putInt(length);
        buffer.putInt(seqNum);
        buffer.put(data, 0, length - headerSize);
        return buffer.array();
    }

    public static Packet getPacket(byte[] bytes) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int type = buffer.getInt();
        int length = buffer.getInt();
        int seqNum = buffer.getInt();
        if (length > headerSize) {
            byte[] data = new byte[length - headerSize];
            buffer.get(data, 0, length - headerSize);
            return new Packet(type, length, seqNum, data);
        } else {
            return new Packet(type, length, seqNum, new byte[0]);
        }
    }
}
