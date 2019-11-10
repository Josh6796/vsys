package dslab.monitoring;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.Config;

public class MonitoringServer implements IMonitoringServer {

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */

    private DatagramSocket server;
    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private volatile boolean running = true;

    public MonitoringServer(String componentId, Config config, InputStream in, PrintStream out) {
        // TODO
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        // TODO
        Shell shell = new Shell(this.in, this.out)
                .register("shutdown", (input, context) -> {
                    shutdown();
                    throw new StopShellException();
                });


        shell.run();
    }

    @Override
    public void addresses() {
        // TODO
    }

    @Override
    public void servers() {
        // TODO
    }

    @Override
    public void shutdown() {
        // TODO
        running = false;
    }

    public static void main(String[] args) throws Exception {
        IMonitoringServer server = ComponentFactory.createMonitoringServer(args[0], System.in, System.out);
        server.run();
    }

}
