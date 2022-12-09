package dslab.transfer;

import dslab.Mail;
import dslab.util.Config;
import dslab.util.Reader;
import dslab.util.Writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Properties;

public class TransferClient implements Runnable {

    private TransferServer transferServer;
    private Properties domains;
    private Mail mail;
    private Mail tempMail = new Mail();
    private Reader reader = null;
    private Writer writer = null;
    private Socket socket = null;
    private DatagramSocket datagramSocket = null;
    private Config config;

    public TransferClient(TransferServer transferServer, Properties domains, String s, Mail mail, Config config) {
        this.transferServer = transferServer;
        this.domains = domains;
        this.tempMail.setSender(mail.getSender());
        this.tempMail.setSubject(mail.getSubject());
        this.tempMail.setData(mail.getData());
        ArrayList<String> tempRec = new ArrayList<>();
        tempRec.add(s);
        tempMail.setRecipients(tempRec);
        this.mail = mail;
        this.config = config;
    }

    @Override
    public void run() {
        String recipient = tempMail.getRecipients().get(0);
        String[] emailParts = recipient.split("@");
        String emailDomain = emailParts[1];

        if (!domains.containsKey(emailDomain)) {
            ArrayList<String> tempSender = new ArrayList<>();
            tempSender.add(mail.getSender());

            tempMail.setRecipients(tempSender);
            tempMail.setSender("mailer@127.0.0.1");
            tempMail.setSubject("error");
            tempMail.setData("error could not send message");

        }
        String tempDomains = domains.getProperty(emailDomain);
        if (tempDomains != null){
            String[] domainParts = tempDomains.split(":");
            sendMail(domainParts, recipient);
        } else {
            System.out.println("should not happen"); //should not happen, no
        }
    }

    private boolean readAndCompareResponse(String expected) throws IOException {
        return reader.read().equals(expected);
    }

    private void talkToServer(String recipient) throws IOException {
        if(!readAndCompareResponse("ok DMTP")) return;
        writer.write("begin");
        if(!readAndCompareResponse("ok")) return;
        writer.write("to " + recipient);
        if(!readAndCompareResponse("ok 1")) return;
        writer.write("from " + tempMail.getSender());
        if(!readAndCompareResponse("ok")) return;
        writer.write("subject " + tempMail.getSubject());
        if(!readAndCompareResponse("ok")) return;
        writer.write("data " + tempMail.getData());
        if(!readAndCompareResponse("ok")) return;
        writer.write("send");
        if(!readAndCompareResponse("ok")) return;
        writer.write("quit");
        dataPacket();
    }

    private void sendMail(String[] domainParts, String recipient) {
        try {
            socket = new Socket(domainParts[0], Integer.parseInt(domainParts[1]));
            reader = new Reader(socket.getInputStream());
            writer = new Writer(socket.getOutputStream());
            talkToServer(recipient);
            if (!socket.isClosed()){
                socket.close();
            }
            reader.shut();
            writer.shut();
        } catch (IOException e) {
            if (transferServer.isServerUp()) {
                throw new UncheckedIOException("TransferServer up with IOException", e);
            }
        }
    }

    private void dataPacket() {
        try {
            datagramSocket = new DatagramSocket();
            String ipSender = "127.0.0.1:" + config.getString("tcp.port") + " " + tempMail.getSender();
            byte[] datagramBuffer = ipSender.getBytes();
            InetAddress inetAddress = InetAddress.getByName("localhost");
            DatagramPacket datagramPacket = new DatagramPacket(datagramBuffer, datagramBuffer.length, inetAddress, config.getInt("monitoring.port"));
            datagramSocket.send(datagramPacket);
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException("IOException in TransferClient dataPacket()", e);
        } finally {
            if (!datagramSocket.isClosed() && datagramSocket != null ) {
                datagramSocket.close();
            }
        }
    }

}
