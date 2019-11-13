package dslab.mailbox;

import dslab.exceptions.DMTPException;
import dslab.message.Message;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MailboxDMTPHandler implements Runnable{
    private Socket socket;
    private Config config;
    private Scanner in;
    private PrintWriter out;
    private boolean itHasBegun;
    private boolean[] checks;
    private Message message;
    private Hashtable<String,HashMap<Integer,Message>> userMessages;
    private static AtomicInteger messageCount = new AtomicInteger(0);
    private final static Log logger = LogFactory.getFactory().getInstance(MailboxDMTPHandler.class);

    MailboxDMTPHandler(Socket socket, Config config, Hashtable<String,HashMap<Integer,Message>> userMessages) {
        this.socket = socket;
        this.config = config;
        this.itHasBegun = false;
        this.checks = new boolean[4];
        Arrays.fill(checks, false);
        this.userMessages = userMessages;
    }

    @Override
    public void run() {
        logger.info("Connected: " + socket + " on DMTP Server");
        try {
            setup();
            processCommands();
        } catch (Exception e) {
            logger.error("Error:" + socket + " on DMTP Server");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            logger.info("Closed: " + socket + " on DMTP Server");
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
                            processSendCommand();
                        } else {
                            checkWhichAreFalse(checks);
                        }
                    } else {
                        out.println("error protocol error");
                        throw new DMTPException("error protocol error");
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
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
        for (String address:addresses) {
            if(message.isValidEmailAddress(address)) {
                String user = address.split("@")[0];
                String domain = address.split("@")[1];

                if (config.getString("domain").equals(domain)) {
                    if (config.containsKey(user)) {
                        recipients.add(address);
                    } else {
                        out.println("error unknown recipient " + user);
                        return;
                    }
                }
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

    private void processSendCommand() {
        for (String recipient : message.getRecipients()) {
            String user = recipient.split("@")[0];
            synchronized (userMessages) {
                int id = messageCount.incrementAndGet();
                put(userMessages, user, id, message);
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

    private static void put(Hashtable<String, HashMap<Integer, Message>> ht, String key, int id, Message value) {
        HashMap<Integer, Message> hashMap = ht.computeIfAbsent(key, k -> new HashMap<Integer, Message>());
        hashMap.put(id, value);
    }
}
