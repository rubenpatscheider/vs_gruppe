package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.Mail;
import dslab.util.Reader;
import dslab.util.Writer;

public class MailboxServer implements IMailboxServer, Runnable {

    private volatile boolean serverUp = false;
    private String componentId;
    private Config config;
    private Reader reader;
    private Writer writer;
    private ServerSocket serverSocketDMTP = null;
    private Shell shell;
    private Properties users;
    private HashMap<String, ConcurrentHashMap<Integer, Mail>> messages;
    private AtomicInteger mailId;
    private ServerSocket serverSocketDMAP = null;


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MailboxServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.reader = new Reader(in);
        this.writer = new Writer(out);
        this.shell = new Shell(in ,out);
        shell.register(this);
        shell.setPrompt(">");
        this.users = new Properties();
        try {
            InputStream tempIn = MailboxServer.class.getClassLoader().getResourceAsStream(config.getString("users.config"));
            users.load(tempIn);
        } catch (IOException e) {
            throw new UncheckedIOException("Error loading users", e);
        }
        messages = new HashMap<>();
        for (Enumeration<Object> enumeration = users.keys(); enumeration.hasMoreElements(); ){
            messages.put(enumeration.nextElement().toString(), new ConcurrentHashMap<Integer, Mail>());
        }
        this.mailId = new AtomicInteger(0);
    }

    @Override
    public void run() {
        serverUp = true;
        try {
            serverSocketDMTP = new ServerSocket(config.getInt("dmtp.tcp.port"));
            new DMTPListener(serverSocketDMTP, this).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create DMTP-socket in mail-server", e);
        }
        try {
            serverSocketDMAP = new ServerSocket(config.getInt("dmap.tcp.port"));
            new DMAPListener(serverSocketDMAP, this).start();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create DMAP-socket in mail-server", e);
        }
        shell.run();
    }

    @Override
    @Command
    public void shutdown() {
        serverUp = false;
        if (serverSocketDMTP != null) {
            try{
                serverSocketDMTP.close();
            } catch (IOException ignored) {}
        }
        if (serverSocketDMAP != null) {
            try{
                serverSocketDMAP.close();
            } catch (IOException ignored) {}
        }
        reader.shut();
        writer.shut();

        throw new StopShellException();
    }


    public boolean isServerUp(){
        return this.serverUp;
    }

    public Properties getUsers() {
        return this.users;
    }

    public boolean hasDomain(String domain) {
        return domain.equals(config.getString("domain"));
    }

    public void saveMail(String user, Mail mail) {
        messages.get(user).put(mailId.incrementAndGet(), mail);
    }

    public ArrayList<String> getMails(String user) {
        ArrayList<String> tempMessages = new ArrayList<>();
        for (Map.Entry<Integer, Mail> message : messages.get(user).entrySet()) {
            tempMessages.add(message.getKey() + " " + message.getValue().getSender() + " " + message.getValue().getSubject());
        }

        return tempMessages;
    }

    public Mail getMail(String user, int id) {
        return messages.get(user).get(id);
    }

    public Mail deleteMail(String user, int id) {
        return messages.get(user).remove(id);
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}

