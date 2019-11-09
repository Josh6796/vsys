package dslab.mailbox;

import dslab.exceptions.DMAPException;
import dslab.message.Message;
import dslab.util.Config;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class MailboxDMAPHandler implements Runnable{
    private Socket socket;
    private Config config;
    private Scanner in;
    private PrintWriter out;
    private boolean loggedIn;
    private boolean[] checks;
    private Message message;

    MailboxDMAPHandler(Socket socket, Config config) {
        this.socket = socket;
        this.config = config;
        this.loggedIn = false;
        this.checks = new boolean[4];
        Arrays.fill(checks, false);
    }

    @Override
    public void run() {
        System.out.println("Connected: " + socket + " on DMAP Server");
        try {
            setup();
            processCommands();
        } catch (Exception e) {
            System.out.println("Error:" + socket + " on DMAP Server");
        } finally {
            try { socket.close(); } catch (IOException e) {}
            System.out.println("Closed: " + socket + " on DMAP Server");
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
            if (command.equals("quit")) {
                out.println("ok bye");
                return;
            } else if (command.split(" ", 2)[0].equals("login")) {
                processLoginCommand(command.split(" ", 2)[1]);
            } else if (loggedIn) {
                try {
                    if (command.equals("list")) {
                        processListCommand();
                        checks[0] = true;
                    }
                    else if (command.split(" ", 2)[0].equals("show")) {
                        processShowCommand(command.split(" ")[1]);
                        checks[1] = true;
                    }
                    else if (command.split(" ", 2)[0].equals("delete")) {
                        processDeleteCommand(command.split(" ")[1]);
                        checks[2] = true;
                    }
                    else if (command.equals("logout")) {
                        processLogoutCommand();
                    } else {
                        out.println("error protocol error");
                        throw new DMAPException("error protocol error");
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    out.println("error protocol error");
                    throw new DMAPException("error protocol error");
                }
            } else {
                out.println("error protocol error");
                throw new DMAPException("error protocol error");
            }
        }
    }


    private void processLoginCommand(String login) {
        String user = login.split(" ", 2)[0];
        int password = Integer.parseInt(login.split(" ", 2)[1]);
        if (config.containsKey(user)) {
            if(config.getInt(user) == password) {
                out.println("ok");
                loggedIn = true;
            } else {
                out.println("error wrong password");
            }
        } else {
            out.println("error unknown user");
        }
    }

    private void processListCommand() {
    }

    private void processShowCommand(String s) {
    }

    private void processDeleteCommand(String s) {
    }

    private void processLogoutCommand() {
        loggedIn = false;
    }

    private void checkWhichAreFalse(boolean[] checks) {
        if (!checks[0]) {
            out.println("error no recipients");
        }
        if (!checks[1]) {
            out.println("error no sender");
        }
        if (!checks[2]) {
            out.println("error no subject");
        }
        if (!checks[3]) {
            out.println("error no content");
        }
    }

    private static boolean areAllTrue(boolean[] array)
    {
        for(boolean b : array) if(!b) return false;
        return true;
    }
}
