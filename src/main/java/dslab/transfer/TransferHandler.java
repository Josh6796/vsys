package dslab.transfer;

import dslab.exceptions.DMTPException;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class TransferHandler implements Runnable{
    private Socket socket;
    private Scanner in;
    private PrintWriter out;
    private boolean itHasBegun;

    TransferHandler(Socket socket) {
        this.socket = socket;
        this.itHasBegun = false;
    }

    @Override
    public void run() {
        System.out.println("Connected: " + socket);
        try {
            setup();
            processCommands();
        } catch (Exception e) {
            System.out.println("Error:" + socket);
        } finally {
            try { socket.close(); } catch (IOException e) {}
            System.out.println("Closed: " + socket);
        }
    }

    private void setup() throws IOException {
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);
        out.println("ok DTMP");
    }

    private void processCommands() throws DMTPException {
        while (in.hasNextLine()) {
            var command = in.nextLine();
            if (command.startsWith("quit")) {
                out.println("ok bye");
                return;
            } else if (command.startsWith("begin")) {
                out.println("ok");
                itHasBegun = true;

            } else if (itHasBegun) {
                if (command.startsWith("to")) {
                    processToCommand();
                }
            } else {
                out.println("error protocol error");
                throw new DMTPException("error protocol error");
            }
        }
    }

    private void processToCommand() {

    }
}
