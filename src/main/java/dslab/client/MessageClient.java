package dslab.client;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Mail;
import dslab.util.Config;
import dslab.util.Keys;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.*;
import dslab.util.Reader;
import dslab.util.Writer;

public class MessageClient implements IMessageClient, Runnable {

    private String componentId;
    private Config config;
    private InputStream in;
    private OutputStream out;
    private Shell shell;
    private Socket dmapSocket = null;
    private Socket dmtpSocket = null;
    private BufferedReader dmapReader = null;
    private PrintWriter dmapWriter = null;
    private Cipher cipherAES;
    private SecretKeySpec secretKeySpec;
    private IvParameterSpec ivParameterSpec;

    /**
     * Creates a new client instance.
     *
     * @param componentId the id of the component that corresponds to the Config resource
     * @param config      the component config
     * @param in          the input stream to read console input from
     * @param out         the output stream to write console output to
     */
    public MessageClient(String componentId, Config config, InputStream in, PrintStream out) {
        this.componentId = componentId;
        this.config = config;
        this.in = in;
        this.out = out;
        this.shell = new Shell(in, out);
        shell.register(this);
        shell.setPrompt(">");
    }

    @Override
    public void run() {
        try {
            String response;
            dmapSocket = new Socket(config.getString("mailbox.host"), config.getInt("mailbox.port"));
            //dmtpSocket = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            dmapReader = new BufferedReader(new InputStreamReader(dmapSocket.getInputStream()));
            dmapWriter = new PrintWriter(dmapSocket.getOutputStream(), true);

            response = dmapReader.readLine();
            if(!response.equals("ok DMAP2.0")) {
                shutdown();
            }

            dmapWriter.println("startsecure");
            response = dmapReader.readLine();
            String[] msgSplit = response.split("\\s");

            if(!msgSplit[0].equals("ok") && msgSplit.length != 2) {
                shutdown();
            }

            String answer = "ok";
            SecureRandom random = new SecureRandom();
            byte challenge[] = new byte[32];
            byte iv[] = new byte[16];
            random.nextBytes(challenge);
            random.nextBytes(iv);
            String challengeString = Base64.getEncoder().encodeToString(challenge);
            String ivString = Base64.getEncoder().encodeToString(iv);

            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256); // The AES key size in number of bits
            byte[] keyUser = generator.generateKey().getEncoded();
            String generatorString = Base64.getEncoder().encodeToString(keyUser);

            answer = "ok " + challengeString + " " + generatorString + " " + ivString;

            File file = new File("keys/client/" + msgSplit[1] + "_pub.der");
            PublicKey key = Keys.readPublicKey(file);

            Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            c.init(Cipher.ENCRYPT_MODE, key);
            //c.doFinal(Base64.getDecoder().decode(answer));
            //System.out.println(Base64.getEncoder().encodeToString(c.doFinal(Base64.getDecoder().decode(answer))));
            dmapWriter.println(Base64.getEncoder().encodeToString(c.doFinal(answer.getBytes())));

            cipherAES = Cipher.getInstance("AES/CTR/NoPadding");
            secretKeySpec = new SecretKeySpec(keyUser, "AES");
            ivParameterSpec = new IvParameterSpec(iv);
            cipherAES.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            response = dmapReader.readLine();
            response = new String(cipherAES.doFinal(Base64.getDecoder().decode(response)));
            byte[] challengeServer = Base64.getDecoder().decode(response.split(" ")[1]);

            if (Arrays.equals(challenge,challengeServer)) {
                dmapWriter.println(encrypt("ok"));
            } else {
                shutdown();
            }

            dmapWriter.println(encrypt("login " + config.getString("mailbox.user") + " " + config.getString("mailbox.password")));

            response = decrypt(dmapReader.readLine());
            if (!response.equals("ok")) {
                shutdown();
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            shutdown();
        } catch (NoSuchPaddingException e) {
            shutdown();
        } catch (InvalidKeyException e) {
            shutdown();
        } catch (IllegalBlockSizeException e) {
            shutdown();
        } catch (BadPaddingException e) {
            shutdown();
        } catch (InvalidAlgorithmParameterException e) {
            shutdown();
        }

        shell.run();
    }


    @Command
    @Override
    public void inbox() {
        try {
            List<String> index = new ArrayList<String>();

            dmapWriter.println(encrypt("list"));
            String input = decrypt(dmapReader.readLine());

            if(input.split(" ")[0].equals("error")) {
                shell.out().println(input);
                return;
            }

            String[] inputSplit = input.split("\n");

            for (String split : inputSplit) {
                String[] details = split.split(" ");

                if (details.length > 1) {
                    index.add(details[0]);
                }
            }

            for (String i : index) {
                dmapWriter.println(encrypt("show " + i));

                String show = decrypt(dmapReader.readLine());
                shell.out().println(i + "\r\n" + show);
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InvalidKeyException e) {
            shutdown();
        } catch (IllegalBlockSizeException e) {
            shutdown();
        } catch (BadPaddingException e) {
            shutdown();
        } catch (InvalidAlgorithmParameterException e) {
            shutdown();
        }
    }

    @Command
    @Override
    public void delete(String id) {
        try{
            dmapWriter.println(encrypt("delete " + id));
            shell.out().println(decrypt(dmapReader.readLine()));
        } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException |
                 InvalidKeyException | IOException e) {
            shutdown();
        }
    }

    @Command
    @Override
    public void verify(String id) {
        Mail email = new Mail();
        try{
            dmapWriter.println(encrypt("show" + id));
            String response = decrypt(dmapReader.readLine());

            if(response.startsWith("error")) shell.out().println(response);
            else {
                String[] emailParts = response.split(System.lineSeparator());

                email.setSender(emailParts[0].substring(5));
                email.setRecipients(email.recipientsToArray(emailParts[0].substring(3)));
                email.setSubject(emailParts[2].substring(8));
                email.setData(emailParts[3].substring(5));
                email.setHash(emailParts[4].substring(5));

                String checkHash = findHash(email);

                if(email.getHash() == null) shell.out().println("error no hash attached");
                else if(checkHash == null) shell.out().println("error while calculating hash");
                else{
                    shell.out().println((email.getHash().equals(checkHash)) ? "ok" : "error" );
                }
            }
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException |
                 IllegalBlockSizeException | IOException e) {
            shutdown();
        }

    }

    @Command
    @Override
    public void msg(String to, String subject, String data) {
        Mail mail = new Mail();
        mail.setSender(config.getString("transfer.email"));
        mail.setRecipients(mail.recipientsToArray(to));
        mail.setSubject(subject);
        mail.setData(data);
        mail.setHash(findHash(mail));

        try {
            dmtpSocket = new Socket(config.getString("transfer.host"), config.getInt("transfer.port"));
            Reader dmtpReader = new Reader(dmtpSocket.getInputStream());
            Writer dmtpWriter = new Writer(dmtpSocket.getOutputStream());

            String response = dmtpReader.read();
            if(!response.equals("ok DMTP2.0")) throw new Exception();

            dmtpWriter.write("begin");
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("from " + config.getString("transfer.email"));
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("to " + to);
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("subject " + subject);
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("data " + data);
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("hash " + mail.getHash());
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("send");
            response = dmtpReader.read();
            if(!response.equals("ok")) throw new Exception();

            dmtpWriter.write("quit");
            response = dmtpReader.read();
            if(!response.equals("ok bye")) throw new Exception();

            shell.out().println("ok");

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if(dmtpSocket != null){
                try{
                    dmtpSocket.close();
                } catch (IOException ignored) {

                }
            }
        }
    }

    @Command
    @Override
    public void shutdown() {
        if (dmapSocket != null) {
            try {
                dmapSocket.close();
            } catch (IOException ignored) {
            }
        }
        try {
            dmapReader.close();
            dmapWriter.close();
        } catch (IOException e) {
            throw new UncheckedIOException("IOException MessageClient", e);
        }

        throw new StopShellException();
    }

    private String encrypt(String input) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipherAES.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        return Base64.getEncoder().encodeToString(cipherAES.doFinal(input.getBytes()));
    }

    private String decrypt(String input) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipherAES.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        return new String(cipherAES.doFinal(Base64.getDecoder().decode(input)));
    }

    private String findHash(Mail mail) {
        try {
            SecretKeySpec keySpec = Keys.readSecretKey(new File("./keys/hmac.key"));
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(keySpec);
            String msg = String.join("\n", mail.getSender(), mail.recipientsToString(), mail.getSubject(), mail.getData());
            byte[] hash = hmac.doFinal(msg.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
