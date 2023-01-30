import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
// @Jukka J
// 26.01.2023

public class Tools {

    private static final int sizeOfAck = 12;

    public static void sendPacket(byte[] bytes, InetAddress address, int port, DatagramSocket socket) {
        try {
            DatagramPacket sendPacket = new DatagramPacket(bytes, bytes.length, address, port);
            socket.send(sendPacket);
        } catch (Exception e) {
            System.out.println("Exception when sending packet");
        }
    }

    public static Packet receivePacket(int bufferSize, DatagramSocket socket) throws Exception {
        try {
            byte[] buffer = new byte[bufferSize];
            DatagramPacket receiveDatagram = new DatagramPacket(buffer, buffer.length);
            socket.receive(receiveDatagram);
            return Packet.getPacket(receiveDatagram.getData());
        } catch (Exception e) {
            System.out.println("Exception when receiving packet");
            throw e;
        }
    }

    public static void sendACK(
            int ackNum, InetAddress channelAddress, int channelPort, DatagramSocket socket) throws Exception {
        Tools.sendPacket(Packet.createACK(ackNum).getBytes(), channelAddress, channelPort, socket);
        System.out.println(String.format("Package SEND ACK 12 %s", ackNum));
    }

    public static void sendData(Packet packet, InetAddress channelAddress, int port, DatagramSocket socket) {
        Tools.sendPacket(packet.getBytes(), channelAddress, port, socket);
        System.out.println(String.format("Package SEND DAT %s %s", packet.getLength(), packet.getSeqNum()));
    }

    public static void endSenderSession(
            int seqNum, InetAddress channelAddress, int port, DatagramSocket socket) throws Exception {
        // send EOT
        sendPacket(Packet.createEOT(seqNum).getBytes(), channelAddress, port, socket);
        System.out.println("Package SEND EOT 12 " + seqNum);

        // wait for EOT
        while (true) {
            Packet packet = Tools.receivePacket(sizeOfAck, socket);
            if (packet.getType() == 2) {
                System.out.println("Package RECV EOT 12 " + packet.getSeqNum());
                break;
            } else if (packet.getType() == 1){
                System.out.println("Package RECV ACK 12 " + packet.getSeqNum());
            }
        }
    }

    public static void endReceiverSession(
            Packet packet,
            InetAddress channelAddress,
            int channelPort,
            DatagramSocket socket) throws Exception {
        System.out.println(String.format("Package RECV EOT %s %s", packet.getLength(), packet.getSeqNum()));

        Tools.sendPacket(Packet.createEOT(packet.getSeqNum()).getBytes(), channelAddress, channelPort, socket);
        System.out.println("Package SEND EOT 12 " + packet.getSeqNum());
    }
}
