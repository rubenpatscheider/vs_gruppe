package dslab.mailbox;

import dslab.Mail;
import dslab.util.Keys;
import dslab.util.Reader;
import dslab.util.Writer;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.Socket;
import java.security.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Properties;

public class DMAPHandler implements Runnable{

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private MailboxServer mailbox;
    private boolean serverUp = true;
    private String user;
    private boolean logged = false;
    private Cipher cipherAES;
    private SecretKeySpec secretKeySpec;
    private IvParameterSpec ivParameterSpec;
    private boolean secure = false;

    public DMAPHandler(Socket socket, MailboxServer mailbox) {
        this.socket = socket;
        this.mailbox = mailbox;

    }

    @Override
    public void run() {
        try {
            String msg;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            writer.println("ok DMAP2.0");

            while (serverUp){
                try {
                    msg = reader.readLine();
                    if(msg == null) break;
                } catch (IOException e) {
                    break;
                }

                if(secure) {
                    msg = decrypt(msg);
                }

                String[] msgSplit = msg.split("\\s");

                switch (msgSplit[0]) {
                    case "login":
                        if (msgSplit.length < 3) {
                            write("error no user or password");
                            break;
                        }
                        if (!checkUser(msgSplit[1])) {
                            write("error unknown user");
                            break;
                        }
                        if (!checkPassword(msgSplit[1], msgSplit[2])) {
                            write("error wrong password");
                            break;
                        }
                        logged = true;
                        user = msgSplit[1];
                        write("ok");
                        break;
                    case "startsecure":
                        String response;

                        writer.println("ok " + mailbox.componentId);
                        response = reader.readLine();
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

                        writer.println(Base64.getEncoder().encodeToString(cipherAES.doFinal(("ok " + Base64.getEncoder().encodeToString(challenge)).getBytes())));
                        response = reader.readLine();
                        response = decrypt(response);

                        if (!response.equals("ok")) {
                            shut();
                        }
                        secure = true;
                        break;
                    case "show":
                        if (!logged) {
                            write("error not logged in");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            write("error no message id");
                            break;
                        }
                        Mail tempMail = mailbox.getMail(user, Integer.parseInt(msgSplit[1]));
                        if (tempMail == null){
                            write("error unknown message id");
                            break;
                        }
                        if (secure) {
                            tempMail.toString(writer, cipherAES, secretKeySpec, ivParameterSpec);
                        } else {
                            tempMail.toString(writer);
                        }
                        break;
                    case "list":
                        if (!logged) {
                            write("error not logged in");
                            break;
                        }
                        ArrayList<String> messages = mailbox.getMails(user);
                        if (messages.size() == 0) {
                            write("error no messages for user " + user);
                            break;
                        }
                        String output = "";
                        for (String message : messages) {
                            output = output + message + "\r\n";
                            //write(message);
                        }
                        output = output + "ok";
                        write(output);
                        break;
                    case "delete":
                        if (!logged) {
                            write("error not logged in");
                            break;
                        }
                        if (msgSplit.length < 2) {
                            write("error no message id");
                            break;
                        }
                        Mail deleteMail = mailbox.deleteMail(user, Integer.parseInt(msgSplit[1]));
                        if (deleteMail == null) {
                            write("error unknown message id");
                            break;
                        }
                        write("ok");
                        break;
                    case "logout":
                        if (!logged) {
                            write("error not logged in");
                            break;
                        }
                        logged = false;
                        user = null;
                        write("ok");
                        break;
                    case "quit":
                        serverUp = false;
                        logged = false;
                        user = null;
                        write("ok bye");
                        shut();
                        break;
                    default:
                        write("error protocol error");
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

    private void write(String input) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, InvalidKeyException {
        if(secure) {
            writer.println(encrypt(input));
        } else {
            writer.println(input);
        }
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
        try {
            reader.close();
            writer.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

}
