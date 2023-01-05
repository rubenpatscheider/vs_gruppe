package dslab.client;

import at.ac.tuwien.dsg.orvell.Shell;
import at.ac.tuwien.dsg.orvell.StopShellException;
import at.ac.tuwien.dsg.orvell.annotation.Command;
import dslab.ComponentFactory;
import dslab.Mail;
import dslab.util.Config;
import dslab.util.Keys;
import dslab.util.Reader;
import dslab.util.Writer;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MessageClient implements IMessageClient, Runnable {

    private String componentId;
    private Config config;
    private InputStream in;
    private OutputStream out;
    private Shell shell;
    private Socket dmapSocket = null;
    private Socket dmtpSocket = null;
    private Reader reader = null;
    private Writer writer = null;
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
            reader = new Reader(dmapSocket.getInputStream());
            writer = new Writer(dmapSocket.getOutputStream());

            response = reader.read();
            if(!response.equals("ok DMAP2.0")) {
                shutdown();
            }

            writer.write("startsecure");
            response = reader.read();
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
            writer.write(Base64.getEncoder().encodeToString(c.doFinal(answer.getBytes())));

            cipherAES = Cipher.getInstance("AES/CTR/NoPadding");
            secretKeySpec = new SecretKeySpec(keyUser, "AES");
            ivParameterSpec = new IvParameterSpec(iv);
            cipherAES.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            response = reader.read();
            response = new String(cipherAES.doFinal(Base64.getDecoder().decode(response)));
            byte[] challengeServer = Base64.getDecoder().decode(response.split(" ")[1]);

            if (Arrays.equals(challenge,challengeServer)) {
                writer.write(encrypt("ok"));
            } else {
                shutdown();
            }


            writer.write(encrypt("login " + config.getString("mailbox.user") + " " + config.getString("mailbox.password")));

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
        //list+show
        try {
            List<String> emails = new ArrayList<String>();
            List<String> emailDetails = new ArrayList<String>();
            String input = "";

            writer.write(encrypt("list"));

            while (!input.equals("ok")) {
                input = decrypt(reader.read());

                if (!input.equals("ok")) {
                    emails.add(input);
                    shell.out().println(input);
                }
            }
            //shell.out().println(emails);
            /*input = "";

            for (String mail : emails) {
                writer.write(encrypt("show " + mail.split("\\s")[0]));
                shell.out().println(mail.split("\\s")[0]);
                while (!input.split("\\s")[0].equals("data")) {
                    input = decrypt(reader.read());
                    emailDetails.add(input);
                }
            }*/

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

    }

    @Command
    @Override
    public void verify(String id) {
        //calculate and compare

    }

    @Command
    @Override
    public void msg(String to, String subject, String data) {
        //send
        //create and attach hash
        //encode hash to ascii format
        // use Base64 binary-to-text
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
        reader.shut();
        writer.shut();
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

    public static void main(String[] args) throws Exception {
        IMessageClient client = ComponentFactory.createMessageClient(args[0], System.in, System.out);
        client.run();
    }
}
