package dslab.mailbox;

import dslab.Mail;
import dslab.util.Reader;
import dslab.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Properties;

public class DMAPHandler implements Runnable{

    private Socket socket;
    private Reader reader;
    private Writer writer;
    private MailboxServer mailbox;
    private boolean serverUp = true;
    private String user;
    private boolean logged = false;

    public DMAPHandler(Socket socket, MailboxServer mailbox) {
        this.socket = socket;
        this.mailbox = mailbox;

    }

    @Override
    public void run() {
        try {
            String msg;
            reader = new Reader(socket.getInputStream());
            writer = new Writer(socket.getOutputStream());
            writer.write("ok DMAP");

            while (serverUp){
                try {
                    msg = reader.read();
                    if(msg == null) break;
                } catch (IOException e) {
                    break;
                }

                String[] msgSplit = msg.split("\\s");

                switch (msgSplit[0]) {
                    case "login":
                        if (msgSplit.length < 3) {
                            writer.write("error no user or password");
                            break;
                        }
                        if (!checkUser(msgSplit[1])) {
                            writer.write("error unknown user");
                            break;
                        }
                        if (!checkPassword(msgSplit[1], msgSplit[2])) {
                            writer.write("error wrong password");
                            break;
                        }
                        logged = true;
                        user = msgSplit[1];
                        writer.write("ok");
                        break;
                    case "show":
                        if (!logged) {
                            writer.write("error not logged in");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no message id");
                            break;
                        }
                        Mail tempMail = mailbox.getMail(user, Integer.parseInt(msgSplit[1]));
                        if (tempMail == null){
                            writer.write("error unknown message id");
                            break;
                        }
                        tempMail.toString(writer);
                        break;
                    case "list":
                        if (!logged) {
                            writer.write("error not logged in");
                            break;
                        }
                        ArrayList<String> messages = mailbox.getMails(user);
                        if (messages.size() == 0) {
                            writer.write("no messages for user " + user);
                            break;
                        }
                        for (String message : messages) {
                            writer.write(message);
                        }
                        break;
                    case "delete":
                        if (!logged) {
                            writer.write("error not logged in");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no message id");
                            break;
                        }
                        Mail deleteMail = mailbox.deleteMail(user, Integer.parseInt(msgSplit[1]));
                        if (deleteMail == null) {
                            writer.write("error unknown message id");
                            break;
                        }
                        writer.write("ok");
                        break;
                    case "logout":
                        if (!logged) {
                            writer.write("error not logged in");
                            break;
                        }
                        logged = false;
                        user = null;
                        writer.write("ok");
                        break;
                    case "quit":
                        serverUp = false;
                        logged = false;
                        user = null;
                        writer.write("ok bye");
                        shut();
                        break;
                    default:
                        writer.write("error protocol error");
                        shut();
                }

            }
        } catch (IOException e) {
            if (!mailbox.isServerUp()) {
                shut();
            } else {
                throw new UncheckedIOException("IOException DMAP-Handler", e);
            }
        }

    }

    private boolean checkUser(String name) {
        Properties users = mailbox.getUsers();
        return users.containsKey(name);
    }

    private boolean checkPassword(String name, String password) {
        Properties users = mailbox.getUsers();
        return users.getProperty(name).equals(password);
    }

    public void shut() {
        serverUp = false;
        if (!socket.isClosed() && socket != null) {
            try {
                socket.close();
            } catch (IOException e) {}
        }
        reader.shut();
        writer.shut();
    }

}
