package dslab.transfer;

import dslab.Mail;
import dslab.util.Config;

import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class TransferServerQueue extends Thread{

    private LinkedBlockingQueue<Mail> queue;
    private Properties domains;
    private TransferServer transferServer;
    private ExecutorService threadPool;
    private ArrayList<TransferClient> threads;
    private Config config;

    public TransferServerQueue(Properties domains, TransferServer transferServer, Config config) {
        this.queue = new LinkedBlockingQueue<>();
        this.domains = domains;
        this.transferServer = transferServer;
        this.threadPool = Executors.newFixedThreadPool(50);
        this.threads = new ArrayList<>();
        this.config = config;
    }

    public void run() {
        while (transferServer.isServerUp()) {
            while (!queue.isEmpty()) {
                Mail mail = queue.poll();
                ArrayList<String> rec = mail.getRecipients();
                for (String s : rec) {
                    TransferClient transferClient = new TransferClient(transferServer, domains, s, mail, config);
                    threadPool.execute(transferClient);
                    threads.add(transferClient);
                }
            }
        }
        threadPool.shutdownNow();
    }

    public void save(Mail mail) {
        Mail newMail = new Mail();
        newMail.setSender(mail.getSender());
        newMail.setSubject(mail.getSubject());
        newMail.setData(mail.getData());
        newMail.setRecipients(mail.getRecipients());
        queue.add(newMail);
    }


}
