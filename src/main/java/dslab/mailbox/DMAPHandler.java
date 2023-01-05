package dslab.mailbox;

import dslab.Mail;
import dslab.util.Keys;
import dslab.util.Reader;
import dslab.util.Writer;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.security.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Properties;

public class DMAPHandler implements Runnable{

    private Socket socket;
    private Reader reader;
    private Writer writer;
    private MailboxServer mailbox;
    private boolean serverUp = true;
    private String user;
    private boolean logged = false;
    private Cipher cipherAES;
    private SecretKeySpec secretKeySpec;
    private IvParameterSpec ivParameterSpec;

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
            writer.write("ok DMAP2.0");

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
                    case "startsecure":
                        String response;

                        writer.write("ok " + mailbox.componentId);
                        response = reader.read();
                        //System.out.println(response);
                        File file = new File("keys/server/" + mailbox.componentId + ".der");
                        PrivateKey key = Keys.readPrivateKey(file);
                        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        c.init(Cipher.DECRYPT_MODE, key);
                        //c.doFinal(response.getBytes());
                        response = new String(c.doFinal(Base64.getDecoder().decode(response)));

                        msgSplit = response.split("\\s");
                        byte[] challenge = Base64.getDecoder().decode(msgSplit[1]);
                        byte[] keyUser = Base64.getDecoder().decode(msgSplit[2]);
                        byte[] iv = Base64.getDecoder().decode(msgSplit[3]);

                        cipherAES = Cipher.getInstance("AES/CTR/NoPadding");
                        secretKeySpec = new SecretKeySpec(keyUser, "AES");
                        ivParameterSpec = new IvParameterSpec(iv);
                        cipherAES.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

                        writer.write(Base64.getEncoder().encodeToString(cipherAES.doFinal(("ok " + Base64.getEncoder().encodeToString(challenge)).getBytes())));
                        response = reader.read();
                        response = decrypt(response);

                        if (!response.equals("ok")) {
                            shut();
                        }
                        System.out.println("funktioniert");

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
        }catch (NoSuchAlgorithmException e) {
            shut();
        } catch (NoSuchPaddingException e) {
            shut();
        } catch (InvalidKeyException e) {
            shut();
        } catch (IllegalBlockSizeException e) {
            shut();
        } catch (BadPaddingException e) {
            shut();
        } catch (InvalidAlgorithmParameterException e) {
            shut();
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

    private String encrypt(String input) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipherAES.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        return Base64.getEncoder().encodeToString(cipherAES.doFinal(input.getBytes()));
    }

    private String decrypt(String input) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipherAES.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
        return new String(cipherAES.doFinal(Base64.getDecoder().decode(input)));
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
