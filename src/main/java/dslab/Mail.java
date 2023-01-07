package dslab;

import dslab.util.Writer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Base64;

public class Mail {
    private String sender;
    private String subject;
    private String data;
    private ArrayList<String> recipients;
    private String hash;

    public Mail(String sender, String subject, String data, ArrayList<String> recipients, String hash){
        this.sender = sender;
        this.subject = subject;
        this.data = data;
        this.recipients = recipients;
        this.hash = hash;
    }

    public Mail() {
        this.sender = null;
        this.subject = null;
        this.data = null;
        this.recipients = null;
        this.hash = null;
    }

    public String getSender() {
        return  sender;
    }

    public String getSubject() {
        return  subject;
    }

    public String getData(){ return data; }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setRecipients(ArrayList<String> recipients) {
        this.recipients = recipients;
    }

    public ArrayList<String> getRecipients() {
        return this.recipients;
    }

    public void setHash(String hash) {this.hash = hash;}
    public String getHash() {return hash;}

    public void toString(PrintWriter writer){
        String hashline;
        if(hash != null) hashline = "hash " + hash;
        else hashline = "hash";

        writer.println("from " + sender + "\n\r" + "to " + recipientsToString() +
                "\n\r" + "subject " + subject + "\n\r" + "data " + data + "\n\r" + hashline);
    }

    public String recipientsToString(){
        String recipsString = "";
        for (int i = 0; i < recipients.size(); i++) {
            if (i == recipients.size()-1){
                recipsString += recipients.get(i);
            } else {
                recipsString += recipients.get(i) + ",";
            }
        }
        return recipsString;
    }

    public ArrayList<String> recipientsToArray(String recipients){
        ArrayList<String> newRecipients = new ArrayList<>();
        String[] splitRecipients = recipients.split(",");
        for(String recipient: splitRecipients){
            newRecipients.add(recipient);
        }
        return newRecipients;
    }

    public void toString(PrintWriter writer, Cipher cipher, SecretKeySpec secretKeySpec, IvParameterSpec ivParameterSpec) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        String hashline;
        if(hash != null) hashline = "hash " + hash;
        else hashline = "hash";

        writer.println(Base64.getEncoder().encodeToString(cipher.doFinal(("from " + sender + "\n\r" + "to " + recipientsToString() +
                "\n\r" + "subject " + subject + "\n\r" + "data " + data  + "\n\r" + hashline).getBytes())));

        //writer.write(Base64.getEncoder().encodeToString(cipher.doFinal(("from " + sender).getBytes())));
        //writer.write(Base64.getEncoder().encodeToString(cipher.doFinal(("to " + recipsString).getBytes())));
        //writer.write(Base64.getEncoder().encodeToString(cipher.doFinal(("subject " + subject).getBytes())));
        //writer.write(Base64.getEncoder().encodeToString(cipher.doFinal(("data " + data).getBytes())));
    }
}
