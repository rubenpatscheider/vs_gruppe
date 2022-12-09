package dslab.monitoring;

import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Reader;
import dslab.util.Writer;

public class MonitoringServer implements IMonitoringServer {

    private String componentId;
    private Config config;
    private boolean serverUp = false;
    private Shell shell;
    private HashMap<String, Integer> servers = new HashMap<>();
    private HashMap<String, Integer> addresses = new HashMap<>();
    private DatagramSocket datagramSocket = null;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.shell = new Shell(in ,out);
        shell.register(this);
        shell.setPrompt(">");
    }

    @Override
    public void run() {
        serverUp = true;
        try {
            datagramSocket = new DatagramSocket(config.getInt("udp.port"));
            new UDPListener(this, datagramSocket).start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
        shell.run();
    }

    public boolean isServerUp(){
        return this.serverUp;
    }

    public void addToUser(String user) {
        if (addresses.containsKey(user)) {
            addresses.put(user, addresses.get(user)+1);
            return;
        }
        addresses.put(user, 1);
    }

    public void addToServer(String server) {
        if (servers.containsKey(server)) {
            servers.put(server, servers.get(server)+1);
            return;
        }
        servers.put(server, 1);
    }

    @Override
    @Command
    public void addresses() {
        for (Map.Entry<String, Integer> address : addresses.entrySet()) {
            shell.out().println(address.getKey() + " " + address.getValue());
        }
    }

    @Override
    @Command
    public void servers() {
        for (Map.Entry<String, Integer> server : servers.entrySet()) {
            shell.out().println(server.getKey() + " " + server.getValue());
        }
    }

    @Override
    @Command
    public void shutdown() {
        serverUp = false;
        if (!datagramSocket.isClosed() && datagramSocket != null) {
            datagramSocket.close();
        }
        throw new StopShellException();
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
