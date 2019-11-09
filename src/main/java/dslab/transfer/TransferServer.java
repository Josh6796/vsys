package dslab.transfer;

import java.io.*;
import java.net.ServerSocket;
import java.util.concurrent.Executors;

import dslab.ComponentFactory;
import dslab.util.Config;

public class TransferServer implements ITransferServer, Runnable {

    private ServerSocket server;
    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;

    /**
     * Creates a new server instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config the component config
     * @param in the input stream to read console input from
     * @param out the output stream to write console output to
     */
    public TransferServer(String componentId, Config config, InputStream in, PrintStream out) {
        // TODO
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        // TODO
        new Thread(() -> {
            try (var listener = new ServerSocket(config.getInt("tcp.port"))) {
                System.out.println("The tranfer server is running...");
                var pool = Executors.newFixedThreadPool(20);
                while (true) {
                    pool.execute(new TransferHandler(listener.accept(), config));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void shutdown() {
        // TODO
        if (server != null) {
            try {
                server.close();
            } catch (IOException e) {
                System.err.println("Error while closing server socket: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
