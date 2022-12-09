package dslab.mailbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DMAPListener extends Thread{

    private ServerSocket serverSocket;
    private MailboxServer mailbox;
    private ExecutorService threadPool;
    private ArrayList<DMAPHandler> threads;

    public DMAPListener(ServerSocket serverSocket, MailboxServer mailbox) {
        this.serverSocket = serverSocket;
        this.mailbox = mailbox;
        this.threadPool = Executors.newFixedThreadPool(50);
        this.threads = new ArrayList<>();
    }

    public void run() {
        while (true) {
            Socket socket;
            try {
                socket = serverSocket.accept();
                DMAPHandler handler = new DMAPHandler(socket, mailbox);
                threadPool.execute(handler);
                threads.add(handler);
            } catch (SocketException e) {
                if (!mailbox.isServerUp()){
                    shut();
                } else {
                    System.err.println("Socket could not be handled. " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public void shut() {
        for (DMAPHandler thread : threads) {
            if (thread != null) {
                thread.shut();
            }
        }
        threadPool.shutdownNow();
    }
}
