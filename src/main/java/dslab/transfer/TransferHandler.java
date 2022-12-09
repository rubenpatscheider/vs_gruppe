package dslab.transfer;

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

public class TransferHandler implements Runnable{

    private TransferServer transferServer;
    private Socket socket;
    private Reader reader = null;
    private Writer writer = null;
    private boolean serverUp = false;
    private boolean beginBool = false;
    private Mail tempMail;
    private boolean[] validMail;
    private Properties domains;

    public TransferHandler(TransferServer transferServer, Socket socket, Properties domains) {
        this.transferServer = transferServer;
        this.socket = socket;
        this.validMail = new boolean[4];
        this.tempMail = new Mail();
        this.domains = domains;
    }

    @Override
    public void run() {
        serverUp = true;
        try {
            reader = new Reader(socket.getInputStream());
            writer = new Writer(socket.getOutputStream());
            String msg = "";
            writer.write("ok DMTP");
            while (serverUp) {
                try {
                    msg = reader.read();
                    if(msg == null) break;
                } catch (IOException e) {
                    break;
                }
                String[] msgSplit = msg.split("\\s");
                switch (msgSplit[0]) {
                    case "begin":
                        beginBool = true;
                        writer.write("ok");
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
                            writer.write("error enter only one sender");
                            break;
                        }
                        if (!checkEmail(msgSplit[1])){
                            writer.write("error email not valid");
                            break;
                        }
                        tempMail.setSender(msgSplit[1]);
                        validMail[0] = true;
                        writer.write("ok");
                        break;
                    case "to":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no recipient");
                            break;
                        }
                        String[] recsArray = msgSplit[1].split(",");
                        ArrayList<String> recsList = new ArrayList<>();
                        for (String s : recsArray) {
                            if (!checkEmail(s)) {
                                writer.write("error email not valid: " + s);
                                break;
                            }
                            if (!checkDomains(s)) {
                                writer.write("error unknown domain: " + s);
                                break;
                            }
                            recsList.add(s);
                        }

                        if (!recsList.isEmpty()) {
                            tempMail.setRecipients(recsList);
                            validMail[3] = true;
                            writer.write("ok " + recsList.size());
                        } else {
                            writer.write("error no recipient");
                        }
                        break;
                    case "subject":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no subject");
                            break;
                        }
                        String tempSubject = "";
                        for (int i = 1; i < msgSplit.length; i++) {
                            tempSubject += msgSplit[i] + " ";
                        }
                        tempMail.setSubject(tempSubject);
                        validMail[1] = true;
                        writer.write("ok");
                        break;
                    case "data":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            writer.write("error no data");
                            break;
                        }
                        String tempData = "";
                        for (int i = 1; i < msgSplit.length; i++) {
                            tempData += msgSplit[i] + " ";
                        }
                        tempMail.setData(tempData);
                        validMail[2] = true;
                        writer.write("ok");
                        break;
                    case "send":
                        if (!beginBool) {
                            writer.write("error no begin");
                            break;
                        }
                        if (!validMail[0]) {
                            writer.write("error no sender");
                            break;
                        }
                        if (!validMail[3]) {
                            writer.write("error no recipient");
                            break;
                        }
                        if (!validMail[1]) {
                            writer.write("error no subject");
                            break;
                        }
                        if (!validMail[2]) {
                            writer.write("error no data");
                            break;
                        }
                        sendMail();
                        reset();
                        writer.write("ok");
                        break;
                    case "quit":
                        writer.write("ok bye");
                        reset();
                        shut();
                    default:
                        writer.write("error protocol error");
                        reset();
                        shut();
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException("IOException in TransferHandler", e);
        } finally {
            shut();
        }

    }

    private void sendMail() {
        transferServer.queueMail(tempMail);
    }

    private void reset(){
        beginBool = false;
        tempMail.setSender(null);
        tempMail.setRecipients(null);
        tempMail.setSubject(null);
        tempMail.setData(null);
    }

    private boolean checkDomains(String email) {
        String[] emailParts = email.split("@");
        String emailDomain = emailParts[1];
        String tempDomains = domains.getProperty(emailDomain);
        return tempDomains != null;

    }

    private boolean checkEmail(String email) {
        String regex = "^(.+)@(.+)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    public void shut() {
        serverUp = false;
        if (!socket.isClosed() && socket != null){
            try {
                socket.close();
            } catch (IOException ignored){}
        }
        reader.shut();
        writer.shut();
    }


}
