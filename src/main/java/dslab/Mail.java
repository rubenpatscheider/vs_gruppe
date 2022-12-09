package dslab;

import dslab.util.Writer;

import java.util.ArrayList;

public class Mail {
    private String sender;
    private String subject;
    private String data;
    private ArrayList<String> recipients;

    public Mail(String sender, String subject, String data, ArrayList<String> recipients){
        this.sender = sender;
        this.subject = subject;
        this.data = data;
        this.recipients = recipients;
    }

    public Mail() {
        this.sender = null;
        this.subject = null;
        this.data = null;
        this.recipients = null;
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

    public void toString(Writer writer){
        writer.write("from " + sender);
        String recipsString = "";
        for (int i = 0; i < recipients.size(); i++) {
            if (i == recipients.size()-1){
                recipsString += recipients.get(i);
            } else {
                recipsString += recipients.get(i) + ",";
            }
        }
        writer.write("to " + recipsString);
        writer.write("subject " + subject);
        writer.write("data " + data);
    }



}
