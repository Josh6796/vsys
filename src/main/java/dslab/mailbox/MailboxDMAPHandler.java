package dslab.mailbox;

import dslab.exceptions.DMAPException;
import dslab.message.Message;
import dslab.util.Config;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class MailboxDMAPHandler implements Runnable{
    private Socket socket;
    private Config config;
    private Scanner in;
    private PrintWriter out;
    private boolean loggedIn;
    private Hashtable<String, HashMap<Integer,Message>> userMessages;
    private String currentUser;
    private final static Log logger = LogFactory.getFactory().getInstance(MailboxDMAPHandler.class);

    MailboxDMAPHandler(Socket socket, Config config, Hashtable<String,HashMap<Integer,Message>> userMessages) {
        this.socket = socket;
        this.config = config;
        this.loggedIn = false;
        this.userMessages = userMessages;
    }

    @Override
    public void run() {
        logger.info("Connected: " + socket + " on DMAP Server");
        try {
            setup();
            processCommands();
        } catch (Exception e) {
            logger.error("Error:" + socket + " on DMAP Server");
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            logger.info("Closed: " + socket + " on DMAP Server");
        }
    }

    private void setup() throws IOException {
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);
        out.println("ok DMAP");
    }

    private void processCommands() throws DMAPException {
        while (in.hasNextLine()) {
            var command = in.nextLine();
            try {
                if (command.equals("quit")) {
                    out.println("ok bye");
                    return;
                } else if (command.split(" ", 2)[0].equals("login")) {
                    processLoginCommand(command.split(" ", 2)[1]);
                } else if (command.equals("list")) {
                    if (loggedIn) {
                        processListCommand();
                    } else out.println("error not logged in");
                } else if (command.split(" ", 2)[0].equals("show")) {
                    if (loggedIn) {
                        processShowCommand(command.split(" ",2)[1]);
                    } else out.println("error not logged in");
                } else if (command.split(" ", 2)[0].equals("delete")) {
                    if (loggedIn) {
                        processDeleteCommand(command.split(" ", 2)[1]);
                    } else out.println("error not logged in");
                } else if (command.equals("logout")) {
                    if (loggedIn) {
                        loggedIn = false;
                        out.println("ok");
                    } else out.println("error not logged in");
                } else {
                    out.println("error protocol error");
                    throw new DMAPException("error protocol error");
                }
            } catch (Exception e) {
                e.printStackTrace(out);
            }
        }
    }

    private void processLoginCommand(String login) {
        currentUser = login.split(" ", 2)[0];
        try {
            int password = Integer.parseInt(login.split(" ", 2)[1]);
            if (config.containsKey(currentUser)) {
                if(config.getInt(currentUser) == password) {
                    out.println("ok");
                    loggedIn = true;
                } else {
                    out.println("error wrong password");
                }
            } else {
                out.println("error unknown user");
            }
        } catch(NumberFormatException e) {
            out.println("error wrong password");
        }
    }

    private void processListCommand() {
        Message message;
        if (userMessages.get(currentUser) != null && !userMessages.get(currentUser).isEmpty()) {
            for (int key : userMessages.get(currentUser).keySet()) {
                message = userMessages.get(currentUser).get(key);
                out.println(key + " " + message.getSender() + " " + message.getSubject());
            }
        } else {
            out.println("error no messages");
        }
    }

    private void processShowCommand(String s) {
        try {
            int id = Integer.parseInt(s);
            if (userMessages.get(currentUser) != null && userMessages.get(currentUser).containsKey(id)) {
                Message message = userMessages.get(currentUser).get(id);
                out.println("from " + message.getSender());
                out.println("to " + message.recipientsString());
                out.println("subject " + message.getSubject());
                out.println("data " + message.getContent());
            } else {
                out.println("error no message found");
            }
        } catch(NumberFormatException e) {
            out.println("error id has to be int");
        }
    }

    private void processDeleteCommand(String s) {
        try {
            int id = Integer.parseInt(s);
            if (userMessages.get(currentUser).containsKey(id)) {
                userMessages.get(currentUser).remove(id);
                out.println("ok");
            } else {
                out.println("error unknown message id");
            }
        } catch(NumberFormatException e) {
            out.println("error id has to be int");
        }
    }
}
