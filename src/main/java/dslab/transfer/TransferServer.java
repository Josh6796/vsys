package dslab.transfer;

import java.io.*;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.concurrent.Executors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TransferServer implements ITransferServer, Runnable {

    private ServerSocket socket;
    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private volatile boolean running = true;
    private final static Log logger = LogFactory.getFactory().getInstance(TransferServer.class);

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
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        Shell shell = new Shell(this.in, this.out)
                .register("shutdown", (input, context) -> {
                    shutdown();
                    throw new StopShellException();
                });

        new Thread(() -> {
            try {
                logger.info("The tranfer server is running...");
                socket = new ServerSocket(config.getInt("tcp.port"));
                var pool = Executors.newFixedThreadPool(20);
                while (running) {
                    pool.execute(new TransferHandler(socket.accept(), config));
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
    public void shutdown() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                logger.info("The transfer server is not running anymore...");
            } catch (IOException e) {
                logger.error("Error while closing server socket: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ITransferServer server = ComponentFactory.createTransferServer(args[0], System.in, System.out);
        server.run();
    }

}
