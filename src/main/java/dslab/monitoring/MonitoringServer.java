package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.*;
import java.util.HashMap;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MonitoringServer implements IMonitoringServer {

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */

    private DatagramSocket socket;
    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private volatile boolean running = true;
    private HashMap<String, Integer> senders;
    private HashMap<String, Integer> servers;
    private Shell shell;
    private final static Log logger = LogFactory.getFactory().getInstance(MonitoringServer.class);

    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        senders = new HashMap<String, Integer>();
        servers = new HashMap<String, Integer>();
    }

    @Override
    public void run() {
        shell = new Shell(this.in, this.out)
                .register("shutdown", (input, context) -> {
                    shutdown();
                    throw new StopShellException();
                });
        shell.register("addresses", ((input, context) -> addresses()));
        shell.register("servers", ((input, context) -> servers()));

        new Thread(() -> {
            try {
                byte[] receiveData = new byte[1024];
                socket = new DatagramSocket(config.getInt("udp.port"));
                logger.info("The monitoring server is running...");
                while (running) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    socket.receive(receivePacket);
                    String receiveString = new String(receiveData, 0, receivePacket.getLength());
                    logger.info("Received: " + receiveString);

                    String server = receiveString.split(" ")[0];
                    String sender = receiveString.split(" ")[1];
                    // Put Servers in Hashmap
                    if (!servers.isEmpty() && servers.get(server) != null) {
                        servers.put(server, servers.get(server) + 1);
                    } else servers.put(server, 1);
                    // Put Senders in Hashmap
                    if (!senders.isEmpty() && senders.get(sender) != null) {
                        senders.put(sender, senders.get(sender) + 1);
                    } else senders.put(sender, 1);
                }
            } catch (SocketException e) {
                logger.error("Socket Error: " + e.getMessage());
            } catch (IOException e) {
                logger.error("IO Error: " + e.getMessage());
            }
        }).start();

        shell.run();
    }

    @Override
    public void addresses() {
        for (String sender : senders.keySet()) {
            String output = String.join(" ", sender, senders.get(sender).toString());
            shell.out().println(output);
        }
    }

    @Override
    public void servers() {
        for (String server : servers.keySet()) {
            String output = String.join(" ", server, servers.get(server).toString());
            shell.out().println(output);
        }
    }

    @Override
    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
            logger.info("The monitoring server is not running anymore...");
        }
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }
}
