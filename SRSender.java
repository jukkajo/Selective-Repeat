import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.io.*;
import java.util.Scanner;
// @Jukka J
// 26.01.2023

public class SRSender {

    private static final int ACK_SIZE = 12;
    private static final int BUFFER_SIZE = 500;
    private static final int HEADER_SIZE = 12;
    private static final int SEQNUM_MODULO = 256;
    private final Semaphore available = new Semaphore(10);

    private FileInputStream fileStream;

    static DatagramSocket socket;
    static InetAddress channelAddress;
    static int port;

    private Deque<TimedPacket> queue;
    private Map<Integer, TimedPacket> map;

    static int timeout;

    private int base;
    private int nextSeqNum;

    private volatile boolean sendFinished;

    SRSender(String file, String hostname, int channelPort, int t) throws Exception {
        timeout = t;
        base = 0;
        nextSeqNum = 0;
        port = channelPort;
        channelAddress = InetAddress.getByName(hostname);
        queue = new ArrayDeque<>();
        map = new HashMap<>();
        fileStream = new FileInputStream(file);
        sendFinished = false;
    }

    private void receivePackets() {
        byte[] buffer = new byte[ACK_SIZE];
        DatagramPacket receiveDatagram = new DatagramPacket(buffer, buffer.length);
        Packet packet;

        while (!sendFinished || !queue.isEmpty()) {
            try {
                // get ack number
                socket.receive(receiveDatagram);
                packet = Packet.getPacket(receiveDatagram.getData());
                System.out.println(String.format("Package RECV ACK %s %s", packet.getLength(), packet.getSeqNum()));
                int ackNum = packet.getSeqNum();

                // mark that packet as having been received in the window
                if (map.containsKey(ackNum)) {
                    TimedPacket timedPacket = map.get(ackNum);
                    timedPacket.stopTimer();

                    // move forward the window if ackNum == base
                    if (ackNum == base) {
                        while (!queue.isEmpty() && queue.peek().isAck()) {
                            timedPacket = queue.poll();
                            map.remove(timedPacket.getPacket().getSeqNum());
                            available.release();
                        }
                        base = (timedPacket.getPacket().getSeqNum() + 1) % SEQNUM_MODULO;
                    }
                }

            } catch (Exception e) {
                System.out.println("Exception when receiving datagram packet");
            }
        }
    }

    public void start() throws Exception {

        // create socket to send and receive data
        socket = new DatagramSocket();

        // create new thread to receive ACK packets
        Thread receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                receivePackets();
            }
        });
        receiveThread.start();

        // send file data
        System.out.println("Start to send file data");
        while (true) {
            // make packet with individual timer
            byte[] buffer = new byte[BUFFER_SIZE];
            int readNum = fileStream.read(buffer, 0, BUFFER_SIZE);
            if (readNum < 0) {
                sendFinished = true;
                break;
            }
            Packet packet = new Packet(0, readNum + HEADER_SIZE, nextSeqNum, buffer);
            TimedPacket timedPacket = new TimedPacket(packet);

            // send the packet and start its timer
            available.acquire();
            queue.offer(timedPacket);
            map.put(nextSeqNum, timedPacket);
            timedPacket.startTimer();
            Tools.sendData(packet, channelAddress, port, socket);

            // update nextSeqNum
            nextSeqNum = (nextSeqNum + 1) % SEQNUM_MODULO;
        }

        // join the receive thread
        receiveThread.join();

        // end sender session
        Tools.endSenderSession(base, channelAddress, port, socket);
        System.out.println("Finish sending file");
        socket.close();
        fileStream.close();
    }
    public static void main(String[] args) throws Exception {
  
        System.out.println("<-Selective Repeat->");
        System.out.println("<-First, you are supposed to provide timeout, filename to be send, port number and optionally hostname->");

        // Params for constructor
        System.out.println("Give timeout in ms (e.g 1000):");
        Scanner sc2 = new Scanner(System.in);
        int timeout = Integer.parseInt(sc2.nextLine());
        System.out.println("Give filename (e.g important_stuff.txt):");
        Scanner sc3 = new Scanner(System.in);
        String fileName = sc3.nextLine();
        System.out.println("Give port number (e.g 9999):");
        Scanner sc4 = new Scanner(System.in);
        int port = Integer.parseInt(sc4.nextLine());
        String hostName = "localhost";
        System.out.println("Press enter to use localhost, any other key + enter to define different hostname:");
        Scanner sc5 = new Scanner(System.in);
        String defOrOwn = sc3.nextLine();
        if (defOrOwn.length() > 0) {
             System.out.println("Give hostname:");
             Scanner sc6 = new Scanner(System.in);
             hostName = sc6.nextLine();
        }

        // This use file for input instead of regular Scanner approach
        
        // Check input file
        File f = new File(fileName);
        if (!f.exists() || !f.canRead()) {
            throw new Exception("File with given name not found :(");
        }
        SRSender sender = new SRSender(fileName, hostName, port, timeout);
        sender.start();
    }
}
