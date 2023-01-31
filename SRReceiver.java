import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
// @Jukka J
// 27.01.2023

public class SRReceiver {


    private static final int winSize = 10;
    private static final int bufferSize = 512;
    private static final int seqNumMod = 256;
    private int base;

    private Map<Integer, Packet> map;

    private DatagramSocket socket;
    private FileOutputStream foStream;

    private InetAddress channelAddress;
    private int channelPort;
    private boolean getChannelInfo;

    SRReceiver(DatagramSocket socket, String file) throws Exception {
        this.socket = socket;
        foStream = new FileOutputStream(file);
        base = 0;
        getChannelInfo = false;
        map = new HashMap<>();
    }

    // Here we check if ackNum belong in the receiver's window
    private boolean withinWindow(int ackNum) {
        int distance = ackNum - base;
        if (ackNum < base) {
            distance += seqNumMod;
        }
        return distance < winSize;
    }

    // Check if ackNum is in receiver's previous window
    private boolean withinPrevWindow(int ackNum) {
        int distance = base - ackNum;
        if (base < ackNum) {
            distance += seqNumMod;
        }
        return distance <= winSize && distance > 0;
    }

    public void start() throws Exception {

        byte[] buffer = new byte[bufferSize];
        DatagramPacket receiveDatagram = new DatagramPacket(buffer, buffer.length);

        System.out.println("Receiver active (Listening):");
        while(true) {
            
            socket.receive(receiveDatagram);
            Packet packet = Packet.getPacket(receiveDatagram.getData());

            // Ask channel info
            if (!getChannelInfo) {
                channelAddress = receiveDatagram.getAddress();
                channelPort = receiveDatagram.getPort();
                getChannelInfo = true;
            }

            if (packet.getType() == 2) {
                // Brea kwhen receiving EOT
                Tools.endReceiverSession(packet, channelAddress, channelPort, socket);
                break;

            } else if (packet.getType() == 0){
                // process data packet
                System.out.println(String.format("PKT RECV DAT %s %s", packet.getLength(), packet.getSeqNum()));
                int ackNum = packet.getSeqNum();
                if (withinWindow(ackNum)) {
                    // send ACK back to sender
                    Tools.sendACK(ackNum, channelAddress, channelPort, socket);

                    // If the packet is not previously received, add to buffer
                    if (!map.containsKey(ackNum)) {
                        map.put(ackNum, packet);
                    }

                    // If true, move the windoq forward
                    if (ackNum == base) {
                        while (map.containsKey(ackNum)) {
                            foStream.write(map.get(ackNum).getData());
                            map.remove(ackNum);
                            ackNum = (ackNum + 1) % seqNumMod;
                        }
                        base = ackNum % seqNumMod;
                    }

                } else if (withinPrevWindow(ackNum)) {
                    // iF the packet belongs in receiver's previous window -> send ACK
                    Tools.sendACK(ackNum, channelAddress, channelPort, socket);
                }
            }

        }

        // close socket and file outputstream
        System.out.println("Receiving process complete :)");
        foStream.close();
        socket.close();
    }
    
    public static void main(String[] args) throws Exception {
    
         System.out.println("Give filename (e.g important_stuff.txt):");
         Scanner sc3 = new Scanner(System.in);
         String fileName = sc3.nextLine();
         
         System.out.println("Give port number (e.g 9998):");
         Scanner sc4 = new Scanner(System.in);
         int port = Integer.parseInt(sc4.nextLine());
        
         DatagramSocket socket  = new DatagramSocket(port);
         // Filename to store received data
         SRReceiver receiver = new SRReceiver(socket, fileName);
         receiver.start();
    }
}

