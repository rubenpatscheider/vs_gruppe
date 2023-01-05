package dslab.nameserver;

import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Array;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.util.Config;
import dslab.util.Reader;
import dslab.util.Writer;

public class Nameserver implements INameserver, INameserverRemote {

    private String componentId;
    private Config config;
    private Writer writer;
    private Reader reader;
    private Shell shell;
    private String root;
    private String rmiRegistryHostName;
    private String domain;
    private int rmiRegistryPort;
    private boolean isRoot;
    private Registry registry;
    private INameserverRemote nameserverRemote;
    private ConcurrentHashMap<String, INameserverRemote> zones = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> mailServer = new ConcurrentHashMap<>();


    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public Nameserver(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.reader = new Reader(in);
        this.writer = new Writer(out);
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(">");

        this.root = config.getString("root_id");
        this.rmiRegistryPort = config.getInt("registry.port");
        this.rmiRegistryHostName = config.getString("registry.host");
        if (config.containsKey("domain")) {
            domain = config.getString("domain");
            isRoot = false;
        } else {
            domain = "root";
            isRoot = true;
        }
    }

    @Override
    public void run() {
        if (isRoot) runAsRoot();
        else runNormal();

        writer.write("# nameserver '" + domain + "'");
        shell.run();
    }

    public void runAsRoot() {
        try {
            registry = LocateRegistry.createRegistry(rmiRegistryPort);
            INameserverRemote iNameserverRemote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
            registry.bind(root, iNameserverRemote);
        } catch (AlreadyBoundException e) {
            throw new RuntimeException("Error binding name: ", e);
        } catch (RemoteException e) {
            throw new RuntimeException("Error exporting registry: ", e);
        }
    }

    public void runNormal() {
        try {
            registry = LocateRegistry.getRegistry(rmiRegistryHostName, rmiRegistryPort);
            nameserverRemote = (INameserverRemote) registry.lookup(root);
            INameserverRemote iNameserverRemote = (INameserverRemote) UnicastRemoteObject.exportObject(this, 0);
            nameserverRemote.registerNameserver(domain, iNameserverRemote);
        } catch (RemoteException e) {
            throw new RuntimeException("Error exporting registry: ", e);
        } catch (NotBoundException e) {
            throw new RuntimeException("Error during lookup: ", e);
        } catch (AlreadyRegisteredException e) {
            throw new RuntimeException("Error registering domain: ", e);
        } catch (InvalidDomainException e) {
            throw new RuntimeException("Error registering server with given domain: ", e);
        }
    }

    @Override
    @Command
    public void nameservers() {
        if (!zones.isEmpty()) {
            ArrayList<String> relevantZones = new ArrayList<>(zones.keySet());
            relevantZones.sort(String::compareTo);
            for (int i = 0; i < zones.size(); i++) {
                writer.write(i + ". " + zones.get(i));
            }
        }
        writer.write("");
    }

    @Override
    @Command
    public void addresses() {
        if (!mailServer.isEmpty()) {
            ArrayList<String> domains = new ArrayList<>(mailServer.keySet());
            domains.sort(String::compareTo);
            for (int i = 0; i < domains.size(); i++) {
                writer.write(i + ". " + domains.get(i) + ":" + mailServer.get(domains.get(i)));
            }
        }
        writer.write("");
    }

    @Override
    @Command
    public void shutdown() {
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            System.err.println("Error removing object from RMI: " + e.getMessage());
        }
        if (isRoot) {
            try {
                registry.unbind(root);
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (NotBoundException | RemoteException e ) {
                System.err.println("Error removing root object from RMI: " + e.getMessage());
            }
        }
        writer.write(LocalTime.now() + ": nameserver '" + domain + "' shutdown successfully");
        throw new StopShellException();
    }

    @Override
    public void registerNameserver(String domain, INameserverRemote nameserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        domain =domain.toLowerCase();
        String[] domainSplit = domain.split("\\.");
        int dsLength = domainSplit.length -1;
        if (domainSplit.length > 1) {
            String relevantPart = checkAndCalcRelevantParts(dsLength, domainSplit, domain);
            INameserverRemote zone = zones.get(domainSplit[dsLength]);
            zone.registerNameserver(relevantPart, nameserver);
        } else {
            if (isDomainValid(domain)) {
                throw new InvalidDomainException("Error: domain content or format invalid");
            }
            if (!zones.containsKey(domain)) {
                zones.put(domain, nameserver);
            } else {
                throw new AlreadyRegisteredException("Error: Zone already in existence");
            }
            writer.write(LocalTime.now() + ": Registering nameserver for zone '" + domain + "'");
        }
    }

    @Override
    public void registerMailboxServer(String domain, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        domain =domain.toLowerCase();
        String[] domainSplit = domain.split("\\.");
        int dsLength = domainSplit.length -1;
        if (domainSplit.length > 1) {
            String relevantPart = checkAndCalcRelevantParts(dsLength, domainSplit, domain);
            INameserverRemote zone = zones.get(domainSplit[dsLength]);
            zone.registerMailboxServer(relevantPart, address);
        } else {
            if (isDomainValid(domain)) {
                throw new InvalidDomainException("Error: domain content or format invalid");
            }
            if (!mailServer.containsKey(domain)) {
                mailServer.put(domain, address);
            } else {
                throw new AlreadyRegisteredException("Error: mailServer already in existence");
            }
            writer.write(LocalTime.now() + ": Registering mailServer for zone '" + domain + "'");
        }
    }

    public String checkAndCalcRelevantParts(int len, String[] domainSplit, String domain) throws InvalidDomainException, AlreadyRegisteredException, RemoteException {
        if (isDomainValid(domainSplit[len])) {
            throw new InvalidDomainException("Error: domain content or format invalid");
        }
        if (zones.containsKey(domainSplit[len])) {
            int index = domain.length() - domainSplit[len].length() - 1 ;
            return domain.substring(0, index);
        } else {
            throw new InvalidDomainException("Error: domain does not contain zone.");
        }
    }

    public boolean isDomainValid(String domain) {
        return !Pattern.matches(".*[a-zA-Z]+.*[a-zA-Z]", domain);
    }

    @Override
    public INameserverRemote getNameserver(String zone) throws RemoteException {
        writer.write(LocalTime.now() + ": Nameserver for '" + domain + "' requested by transfer server");
        return zones.get(zone);
    }

    @Override
    public String lookup(String username) throws RemoteException {
        writer.write(LocalTime.now() + ": lookup() Zone '" + domain + "'");
        return username.toLowerCase() + ": " + mailServer.get(username.toLowerCase());
    }

    public static void main(String[] args) throws Exception {
        INameserver component = ComponentFactory.createNameserver(args[0], System.in, System.out);
        component.run();
    }


}
