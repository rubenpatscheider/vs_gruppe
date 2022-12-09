package dslab.transfer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferListener extends Thread{

    private TransferServer transferServer;
    private ServerSocket serverSocket;
    private Socket socket = null;
    private ExecutorService threadPool;
    private ArrayList<TransferHandler> threads;
    private Properties domains;

    public TransferListener(TransferServer transferServer, ServerSocket serverSocket, Properties domains) {
        this.transferServer = transferServer;
        this.serverSocket = serverSocket;
        this.threadPool = Executors.newFixedThreadPool(50);
        this.threads = new ArrayList<>();
        this.domains = domains;
    }

    public void run() {
        while (true) {
            try {
                socket = serverSocket.accept();
                TransferHandler transferHandler = new TransferHandler(transferServer, socket, domains);
                threadPool.execute(transferHandler);
                threads.add(transferHandler);
            } catch (SocketException e) {
                if (!transferServer.isServerUp()) {
                    shut();
                } else {
                    System.err.println("TransferServer up and SocketException" + e.getMessage());
                }
                break;
            } catch (IOException e) {
                throw new UncheckedIOException("Could not accept serverSocket", e);
            }
        }
    }

    private void shut() {
        for (int i = 0; i < threads.size(); i++) {
            if (threads.get(i) != null) {
                threads.get(i).shut();
            }
        }
        threadPool.shutdownNow();
    }

}
