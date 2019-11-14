package dslab.transfer;

import dslab.exceptions.DMTPException;
import dslab.message.Message;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class TransferHandler implements Runnable{
    private Socket socket;
    private Config config;
    private Scanner in;
    private PrintWriter out;
    private boolean itHasBegun;
    private boolean[] checks;
    private Message message;
    private BlockingQueue<Message> messageQueue;
    private final static Log logger = LogFactory.getFactory().getInstance(TransferHandler.class);

    TransferHandler(Socket socket, Config config) {
        this.socket = socket;
        this.config = config;
        this.itHasBegun = false;
        this.checks = new boolean[4];
        Arrays.fill(checks, false);
        messageQueue = new ArrayBlockingQueue<>(10);
    }

    @Override
    public void run() {
        logger.info("Connected: " + socket);
        try {
            setup();
            startSendCommandThread();
            processCommands();
        } catch (Exception e) {
            logger.error("Error:" + socket);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            logger.info("Closed: " + socket);
        }
    }

    private void setup() throws IOException {
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);
        out.println("ok DMTP");
    }

    private void processCommands() throws DMTPException {
        while (in.hasNextLine()) {
            var command = in.nextLine();
            if (command.equals("quit")) {
                out.println("ok bye");
                return;
            } else if (command.equals("begin")) {
                out.println("ok");
                this.message = new Message();
                itHasBegun = true;
            } else if (itHasBegun) {
                try {
                    if (command.split(" ", 2)[0].equals("to")) {
                        processToCommand(command.split(" ", 2)[1]);
                        checks[0] = true;
                    }
                    else if (command.split(" ", 2)[0].equals("from")) {
                        processFromCommand(command.split(" ", 2)[1]);
                        checks[1] = true;
                    }
                    else if (command.split(" ", 2)[0].equals("subject")) {
                        message.setSubject(command.split(" ", 2)[1]);
                        out.println("ok");
                        checks[2] = true;
                    }
                    else if (command.split(" ", 2)[0].equals("data")) {
                        message.setContent(command.split(" ", 2)[1]);
                        out.println("ok");
                        checks[3] = true;
                    }
                    else if (command.equals("send")) {
                        if (areAllTrue(checks)) {
                            out.println("ok");
                            messageQueue.put(message);
                        } else {
                            checkWhichAreFalse(checks);
                        }
                    } else {
                        out.println("error protocol error");
                        throw new DMTPException("error protocol error");
                    }
                } catch (ArrayIndexOutOfBoundsException | InterruptedException e) {
                    out.println("error protocol error");
                    throw new DMTPException("error protocol error");
                }
            } else {
                out.println("error protocol error");
                throw new DMTPException("error protocol error");
            }
        }
    }

    private void processToCommand(String s) throws DMTPException {
        String[] addresses = s.split(",");
        ArrayList<String> recipients = new ArrayList<>();

        if (s.isEmpty()) {
            out.println("error protocol error");
            throw new DMTPException("error protocol error");
        }
        for (String  address:addresses) {
            if (message.isValidEmailAddress(address)) {
                recipients.add(address);
            } else {
                out.println("error email address not valid");
                throw new DMTPException("error email address not valid");
            }
        }
        if (!recipients.isEmpty()) {
            out.println("ok " + recipients.size());
            message.setRecipients(recipients);
        } else {
            out.println("error no recipients");
            throw new DMTPException("error no recipients");
        }
    }

    private void processFromCommand(String sender) throws DMTPException {
        if (message.isValidEmailAddress(sender)) {
            out.println("ok");
            message.setSender(sender);
        } else {
            out.println("error email address not valid");
            throw new DMTPException("error email address not valid");
        }
    }

    private void processSendCommand() throws IOException {
        Hashtable<String,List<String>> domainsWithUsers = new Hashtable<String,List<String>>();

        for (String recipient : message.getRecipients()) {
            String user = recipient.split("@")[0];
            String domain = recipient.split("@")[1];
            if (config.containsKey(domain)) {
                put(domainsWithUsers, domain, user);
            }
        }
        for (String domain : domainsWithUsers.keySet()) {
            String host = config.getString(domain).split(":")[0];
            int port = Integer.parseInt(config.getString(domain).split(":")[1]);
            try {
                var socket = new Socket(host, port);
                var inSend = new Scanner(socket.getInputStream());
                var outSend = new PrintWriter(socket.getOutputStream(), true);
                outSend.write("begin\n");
                outSend.flush();
                StringJoiner joiner = new StringJoiner(",");
                for (String user : domainsWithUsers.get(domain)) {
                    joiner.add(user + "@" + domain);
                }
                outSend.write("to " + joiner.toString() + "\n");
                outSend.flush();
                if (inSend.nextLine().startsWith("error unknown recipient")) {
                    sendErrorMessage();
                    return;
                }
                outSend.write("from " + message.getSender() + "\n");
                outSend.flush();
                outSend.write("subject " + message.getSubject() + "\n");
                outSend.flush();
                outSend.write("data " + message.getContent() + "\n");
                outSend.flush();
                outSend.write("send\n");
                outSend.flush();
                outSend.write("quit\n");
                outSend.flush();

                sendDataToMonitoringServer(message.getSender());
            } catch (ConnectException e) {
                sendErrorMessage();
                return;
            }
        }
    }

    private void sendDataToMonitoringServer(String address) throws IOException {
        String server = InetAddress.getLocalHost() + ":" + config.getInt("tcp.port");
        DatagramSocket socket = new DatagramSocket();
        String data = String.join(" ", server, address);
        byte[] sendData = data.getBytes();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, InetAddress.getByName(config.getString("monitoring.host")), config.getInt("monitoring.port"));
        socket.send(sendPacket);
        socket.close();
    }

    private void startSendCommandThread() throws InterruptedException {
        final Runnable consumer = () -> {
            while (true) {
                try {
                    message = messageQueue.take();
                    processSendCommand();
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        };
        new Thread(consumer).start();
    }

    private void sendErrorMessage() throws IOException {
        String user = message.getSender().split("@")[0];
        String domain = message.getSender().split("@")[1];

        if (config.containsKey(domain)) {
            String host = config.getString(domain).split(":")[0];
            int port = Integer.parseInt(config.getString(domain).split(":")[1]);
            try {
                var socket = new Socket(host, port);
                var inSend = new Scanner(socket.getInputStream());
                var outSend = new PrintWriter(socket.getOutputStream(), true);
                outSend.write("begin\n");
                outSend.flush();
                outSend.write("to " + message.getSender() + "\n");
                outSend.flush();
                if (inSend.nextLine().startsWith("error unknown recipient")) {
                    return;
                }
                outSend.write("from " + message.getSender() + "\n");
                outSend.flush();
                outSend.write("subject error mail not delivered\n");
                outSend.flush();
                outSend.write("data error unkown recipient " + user + "\n");
                outSend.flush();
                outSend.write("send\n");
                outSend.flush();
                outSend.write("quit\n");
                outSend.flush();
            } catch (ConnectException e) {
                logger.error("Error can't reach " + config.getString(domain) + " ... message discarded");
            }
        }
    }

    private void checkWhichAreFalse(boolean[] checks) {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("error");
        if (!checks[0]) {
            joiner.add("no recipients");
        }
        if (!checks[1]) {
            joiner.add("no sender");
        }
        if (!checks[2]) {
            joiner.add("no subject");
        }
        if (!checks[3]) {
            joiner.add("no content");
        }
        out.println(joiner.toString());
    }

    private static boolean areAllTrue(boolean[] array)
    {
        for(boolean b : array) if(!b) return false;
        return true;
    }

    private static void put(Hashtable<String,List<String>> ht, String key, String value) {
        List<String> list = ht.computeIfAbsent(key, k -> new ArrayList<String>());
        list.add(value);
    }
}
