package dslab.mailbox;

import dslab.Mail;
import dslab.util.Reader;
import dslab.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DMTPHandler implements Runnable {
    private Socket socket;
    private Reader reader;
    private Writer writer;
    private MailboxServer mailbox;
    private boolean serverUp = true;
    private boolean beginBool = false;
    private ArrayList<String> validRecipients;
    private boolean[] validMessage; //0 = sender, 1 = subject, 2 = data, 3 = has recipients
    private String[] message;

    public DMTPHandler(Socket socket, MailboxServer mailbox) {
        this.socket = socket;
        this.mailbox = mailbox;
        this.validRecipients = null;
        validMessage = new boolean[4];
        message = new String[4];
    }

    @Override
    public void run(){
        try {
            String msg;
            reader = new Reader(socket.getInputStream());
            writer = new Writer(socket.getOutputStream());
            writer.write("ok DMTP2.0");

            while (serverUp){
                try {
                    msg = reader.read();
                    if(msg == null) break;
                } catch (IOException e) {
                    break;
                }
                String[] msgSplit = msg.split("\\s");

                switch (msgSplit[0]) {
                    case "begin":
                        writer.write("ok");
                        beginBool = true;
                        break;
                    case "quit":
                        writer.write("ok bye");
                        reset();
                        shut();
                        break;
                    case "from":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no sender");
                            break;
                        }
                        if (msgSplit.length > 2) {
                            writer.write("error only one sender");
                            break;
                        }
                        if (!checkEmail(msgSplit[1])) {
                            writer.write("error email not valid");
                            break;
                        }
                        validMessage[0] = true;
                        message[0] = msgSplit[1];
                        writer.write("ok");
                        break;
                    case "to":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no recipient");
                        } else {
                            String[] recipients = msgSplit[1].split(",");
                            validRecipients = new ArrayList<>();
                            boolean mailFlag = true;
                            boolean userFlag = true;
                            boolean domainFlag = true;
                            for (String recipient : recipients) {
                                if (!checkEmail(recipient)) {
                                    writer.write("error email not valid");
                                    mailFlag = false;
                                    break;
                                }
                                if (!checkUser(recipient.split("@")[0])) {
                                    writer.write("error unknown recipient " + recipient.split("@")[0]);
                                    userFlag = false;
                                    break;
                                }
                                if (!mailbox.hasDomain(recipient.split("@")[1])) {
                                    writer.write("error unknown domain " + recipient.split("@")[1]);
                                    domainFlag = false;
                                    break;
                                }
                                validRecipients.add(recipient);
                            }
                            if (mailFlag && userFlag && domainFlag) {
                                validMessage[3] = true;
                                writer.write("ok " + validRecipients.size());
                            }
                        }
                        break;
                    case "subject":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no subject");
                        } else {
                            message[1] = "";
                            for (int i = 1; i < msgSplit.length; i++) {
                                message[1] += msgSplit[i] + " ";
                            }
                            /* apparently subject can be empty
                            if (message[1].isBlank()) {
                                writer.write("error no subject");
                                break;
                            }
                             */
                            validMessage[1] = true;
                            writer.write("ok");
                        }
                        break;
                    case "data":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no data");
                        } else {
                            message[2] = "";
                            for (int i = 1; i < msgSplit.length; i++) {
                                message[2] += msgSplit[i] + " ";
                            }
                            if (message[2].isBlank()) {
                                writer.write("error no data");
                                break;
                            }
                            validMessage[2] = true;
                            writer.write("ok");
                        }
                        break;
                    case "hash":
                        if(!beginBool){
                            writer.write("error no begin");
                            break;
                        }
                        if(msgSplit.length < 2){
                            writer.write("error no hash");
                        } else {
                            message[3] = msgSplit[1];
                            writer.write("ok");
                        }
                        break;
                    case "send":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (!validMessage[0]) {
                            writer.write("error no sender");
                            break;
                        }
                        if (!validMessage[1]) {
                            writer.write("error no subject");
                            break;
                        }
                        if (!validMessage[2]) {
                            writer.write("error no data");
                            break;
                        }
                        if (!validMessage[3]) {
                            writer.write("error no recipient");
                            break;
                        }
                        sendMessage();
                        writer.write("ok");
                        break;
                    default:
                        writer.write("error protocol error");
                        reset();
                        shut();
                        break;
                }

            }
        } catch (IOException e) {
            if (!mailbox.isServerUp()) {
                shut();
            } else {
                throw new UncheckedIOException("IOException DMTP-Handler", e);
            }
        } finally {
            shut();
        }
    }

    private void sendMessage() {
        Mail mail = new Mail(message[0], message[1], message[2], validRecipients, message[3]);
        for (String validRecipient : validRecipients) {
            mailbox.saveMail(validRecipient.split("@")[0], mail);
        }
        reset();
    }

    private void reset(){
        beginBool = false;
        validRecipients = null;
        message[0] = null;
        message[1] = null;
        message[2] = null;
        message[3] = null;
    }

    private boolean checkEmail(String email) {
        String regex = "^(.+)@(.+)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    private boolean checkUser(String name) {
        Properties users = mailbox.getUsers();
        return users.containsKey(name);
    }

    public void shut() {
        serverUp = false;
        if (!socket.isClosed() && socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
        reader.shut();
        writer.shut();
    }
}
