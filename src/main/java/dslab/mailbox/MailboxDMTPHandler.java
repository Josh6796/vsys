package dslab.mailbox;

import dslab.exceptions.DMTPException;
import dslab.message.Message;
import dslab.util.Config;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class MailboxDMTPHandler implements Runnable{
    private Socket socket;
    private Config config;
    private Scanner in;
    private PrintWriter out;
    private boolean itHasBegun;
    private boolean[] checks;
    private Message message;

    MailboxDMTPHandler(Socket socket, Config config) {
        this.socket = socket;
        this.config = config;
        this.itHasBegun = false;
        this.checks = new boolean[4];
        Arrays.fill(checks, false);
    }

    @Override
    public void run() {
        System.out.println("Connected: " + socket + " on DMTP Server");
        try {
            setup();
            processCommands();
        } catch (Exception e) {
            System.out.println("Error:" + socket + " on DMTP Server");
        } finally {
            try { socket.close(); } catch (IOException e) {}
            System.out.println("Closed: " + socket + " on DMTP Server");
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

        for (String address:addresses) {
            recipients.add(address);
            String user = address.split("@")[0];
            if (!config.containsKey(user)) {
                out.println("error unknown recipient " + user);
                throw new DMTPException("error unknown recipient " + user);
            }
        }
        if (!recipients.isEmpty()) {
            out.println("ok " + recipients.size());
            message.setRecipients(recipients);
        } else {
            out.println("error protocol error");
            throw new DMTPException("error protocol error");
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
        Hashtable<String,List<Message>> userMessages = new Hashtable<String,List<Message>>();

        for (String recipient : message.getRecipients()) {
            String user = recipient.split("@")[0];
            put(userMessages, user, message);
        }
        out.println("Hashtable stored: " + userMessages);
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

    private static void put(Hashtable<String, List<Message>> ht, String key, Message value) {
        List<Message> list = ht.computeIfAbsent(key, k -> new ArrayList<Message>());
        list.add(value);
    }
}
