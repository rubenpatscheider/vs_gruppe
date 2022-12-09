package dslab.monitoring;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UDPListener extends Thread{

    private MonitoringServer monitoringServer;
    private DatagramSocket datagramSocket;
    private DatagramPacket datagramPacket = null;
    private int ARRAY_SIZE = 1024;
    private byte[] datagramBuffer = new byte[ARRAY_SIZE];

    public UDPListener(MonitoringServer monitoringServer, DatagramSocket datagramSocket) {
        this.monitoringServer = monitoringServer;
        this.datagramSocket = datagramSocket;
    }

    public void run() {
        try {
            while (monitoringServer.isServerUp()) {
                datagramPacket = new DatagramPacket(datagramBuffer, ARRAY_SIZE);
                datagramSocket.receive(datagramPacket);
                String datagramData = new String(datagramPacket.getData(), 0, datagramPacket.getLength());
                monitoringServer.addToServer(datagramData.split("\\s")[0]);
                monitoringServer.addToUser(datagramData.split("\\s")[1]);
            }
        } catch (SocketException e) {
            if (monitoringServer.isServerUp()) {
                System.err.println("SocketException in UDPListener. " + e.getMessage());
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException("IOException in UDPListener", e);
        } finally {
            if (!datagramSocket.isClosed() && datagramSocket != null) {
                datagramSocket.close();
            }
        }
    }

}
