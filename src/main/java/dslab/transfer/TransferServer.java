package dslab.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.Properties;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.mailbox.MailboxServer;
import dslab.util.Config;
import dslab.Mail;
import dslab.util.Reader;
import dslab.util.Writer;

public class TransferServer implements ITransferServer, Runnable {

    private String componentId;
    private Config config;
    private Reader reader;
    private Writer writer;
    private boolean serverUp = false;
    private ServerSocket serverSocket = null;
    private Properties domains;
    private Shell shell;
    private TransferServerQueue queue = null;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.reader = new Reader(in);
        this.writer = new Writer(out);
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(">");
        this.domains = new Properties();
        try {
            InputStream tempIn = MailboxServer.class.getClassLoader().getResourceAsStream("domains.properties");
            domains.load(tempIn);
        } catch (IOException e) {
            throw new UncheckedIOException("Domains could not be loaded", e);
        }
    }

    @Override
    public void run() {
        serverUp = true;
        try {
            serverSocket = new ServerSocket(config.getInt("tcp.port"));
            new TransferListener(this, serverSocket, domains).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create socket in TransferServer", e);
        }
        queue = new TransferServerQueue(domains, this, config);
        queue.start();
        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        serverUp = false;
        if (serverSocket != null) {
            try{
                serverSocket.close();
            } catch (IOException e){}
        }
        reader.shut();
        writer.shut();
        throw new StopShellException();
    }

    public boolean isServerUp(){
        return this.serverUp;
    }

    public void queueMail(Mail tempMail) {
        queue.save(tempMail);
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }


}
