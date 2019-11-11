package dslab.mailbox;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.concurrent.Executors;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import dslab.ComponentFactory;
import dslab.message.Message;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MailboxServer implements IMailboxServer, Runnable {

    private ServerSocket dmtpSocket;
    private ServerSocket dmapSocket;
    private String componentId;
    private Config config;
    private InputStream in;
    private PrintStream out;
    private Hashtable<String,HashMap<Integer,Message>> userMessages;
    private volatile boolean dmtpRunning = true;
    private volatile boolean dmapRunning = true;
    private final static Log logger = LogFactory.getFactory().getInstance(MailboxServer.class);

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

        userMessages = new Hashtable<String, HashMap<Integer,Message>>();
        new Thread(() -> {
            try {
                logger.info("The mailbox DMTP server is running...");
                dmtpSocket = new ServerSocket(config.getInt("dmtp.tcp.port"));
                var pool = Executors.newFixedThreadPool(20);
                while (dmtpRunning) {
                    pool.execute(new MailboxDMTPHandler(dmtpSocket.accept(), config, userMessages));
                }
            } catch (SocketException e) {
                logger.error("Socket Error: " + e.getMessage());
            } catch (IOException e) {
                logger.error("IO Error: " + e.getMessage());
            }
        }).start();
        new Thread(() -> {
            try  {
                logger.info("The mailbox DMAP server is running...");
                dmapSocket = new ServerSocket(config.getInt("dmap.tcp.port"));
                var pool = Executors.newFixedThreadPool(20);
                while (dmapRunning) {
                    pool.execute(new MailboxDMAPHandler(dmapSocket.accept(), config, userMessages));
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
        if (dmtpSocket != null && !dmtpSocket.isClosed()) {
            try {
                dmtpRunning = false;
                dmtpSocket.close();
                logger.info("The mailbox DMTP server is not running anymore...");
            } catch (IOException e) {
                logger.error("Error while closing server socket: " + e.getMessage());
            }
        }
        if (dmapSocket != null && !dmapSocket.isClosed()) {
            try {
                dmapRunning = false;
                dmapSocket.close();
                logger.info("The mailbox DMAP server is not running anymore...");
            } catch (IOException e) {
                logger.error("Error while closing server socket: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        IMailboxServer server = ComponentFactory.createMailboxServer(args[0], System.in, System.out);
        server.run();
    }
}
