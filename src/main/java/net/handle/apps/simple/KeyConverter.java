/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.simple;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.*;
import javax.crypto.spec.*;

import net.cnri.util.SimpleCommandLine;
import net.cnri.util.StreamUtil;
import net.handle.hdllib.Common;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.Util;

import org.apache.commons.codec.binary.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class KeyConverter {
    private static class BytesAndKeyType {
        byte[] bytes;
        String keyType;

        public BytesAndKeyType(byte[] bytes, String keyType) {
            this.bytes = bytes;
            this.keyType = keyType;
        }
    }

    private static Pattern firstLinePattern = Pattern.compile("^\\s*-----BEGIN (.*) KEY-----\\s*$");

    private static BytesAndKeyType readPemFile(Reader reader) {
        BufferedReader bufferedReader;
        if (reader instanceof BufferedReader) bufferedReader = (BufferedReader) reader;
        else bufferedReader = new BufferedReader(reader);
        String line;
        StringBuilder base64Only = new StringBuilder();
        String keyType = null;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (keyType == null) {
                    Matcher m = firstLinePattern.matcher(line);
                    if (m.matches()) keyType = m.group(1);
                    else keyType = "";
                }
                if (line.startsWith("-----")) continue;
                base64Only.append(line);
            }
            bufferedReader.close();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        byte[] bytes = Base64.decodeBase64(base64Only.toString());
        return new BytesAndKeyType(bytes, keyType);
    }

    public static String toX509Pem(PublicKey publicKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\r\n");
        byte[] data = Base64.encodeBase64(publicKey.getEncoded(), true);
        for (byte b : data) {
            sb.append((char) b);
        }
        if (data[data.length - 1] != '\n') sb.append("\r\n");
        sb.append("-----END PUBLIC KEY-----\r\n");
        return sb.toString();
    }

    public static PublicKey publicKeyFromBytes(byte[] bytes) throws Exception {
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
            try {
                return KeyFactory.getInstance("RSA").generatePublic(keySpec);
            } catch (InvalidKeySpecException e) {
                return KeyFactory.getInstance("DSA").generatePublic(keySpec);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (InvalidKeySpecException e) {
            throw new Exception("Neither RSA nor DSA public key generator can parse", e);
        }
    }

    public static PublicKey fromX509Pem(String pem) throws Exception {
        BytesAndKeyType bytesAndKeyType = readPemFile(new StringReader(pem));
        if (!"PUBLIC".equals(bytesAndKeyType.keyType)) {
            throw new Exception("Expected -----BEGIN PUBLIC KEY-----");
        }
        return publicKeyFromBytes(bytesAndKeyType.bytes);
    }

    public static String toPkcs8UnencryptedPem(PrivateKey privateKey) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\r\n");
        byte[] data = Base64.encodeBase64(privateKey.getEncoded(), true);
        for (byte b : data) {
            sb.append((char) b);
        }
        if (data[data.length - 1] != '\n') sb.append("\r\n");
        sb.append("-----END PRIVATE KEY-----\r\n");
        return sb.toString();
    }

    public static String toPkcs8EncryptedPem(PrivateKey privateKey, String passphrase) {
        String alg = "PBEWithSHA1AndDESede";
        int count = 10000;// hash iteration count
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] encryptedPkcs8;
        try {
            PBEParameterSpec pbeParamSpec = new PBEParameterSpec(salt, count);
            PBEKeySpec pbeKeySpec = new PBEKeySpec(passphrase.toCharArray());
            SecretKeyFactory keyFac = SecretKeyFactory.getInstance(alg);
            SecretKey pbeKey = keyFac.generateSecret(pbeKeySpec);

            Cipher pbeCipher = Cipher.getInstance(alg);
            pbeCipher.init(Cipher.ENCRYPT_MODE, pbeKey, pbeParamSpec);
            byte[] ciphertext = pbeCipher.doFinal(privateKey.getEncoded());

            // Now construct  PKCS #8 EncryptedPrivateKeyInfo object
            AlgorithmParameters algparms = AlgorithmParameters.getInstance(alg);
            algparms.init(pbeParamSpec);
            EncryptedPrivateKeyInfo encinfo = new EncryptedPrivateKeyInfo(algparms, ciphertext);
            // and here we have it! a DER encoded PKCS#8 encrypted key!
            encryptedPkcs8 = encinfo.getEncoded();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ENCRYPTED PRIVATE KEY-----\r\n");
        byte[] data = Base64.encodeBase64(encryptedPkcs8, true);
        for (byte b : data) {
            sb.append((char) b);
        }
        if (data[data.length - 1] != '\n') sb.append("\r\n");
        sb.append("-----END ENCRYPTED PRIVATE KEY-----\r\n");
        return sb.toString();
    }

    public static PrivateKey privateKeyFromBytes(byte[] bytes, boolean encrypted, String passphrase) throws Exception {
        KeySpec keySpec;
        if (encrypted) {
            if (passphrase == null) {
                throw new Exception("Encrypted key, passphrase required");
            }
            try {
                keySpec = keySpecFromEncryptedBytes(bytes, passphrase);
            } catch (Exception e) {
                throw new Exception("Unable to decrypt private key", e);
            }
        } else {
            keySpec = new PKCS8EncodedKeySpec(bytes);
        }
        try {
            try {
                return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
            } catch (InvalidKeySpecException e) {
                return KeyFactory.getInstance("DSA").generatePrivate(keySpec);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        } catch (InvalidKeySpecException e) {
            throw new Exception("Neither RSA nor DSA private key generator can parse", e);
        }
    }

    public static PrivateKey fromPkcs8Pem(String pem, String passphrase) throws Exception {
        BytesAndKeyType bytesAndKeyType = readPemFile(new StringReader(pem));
        boolean encrypted = "ENCRYPTED PRIVATE".equals(bytesAndKeyType.keyType);
        if (!encrypted && !"PRIVATE".equals(bytesAndKeyType.keyType)) {
            throw new Exception("Expected -----BEGIN [ENCRYPTED] PRIVATE KEY-----");
        }
        return privateKeyFromBytes(bytesAndKeyType.bytes, encrypted, passphrase);
    }

    private static KeySpec keySpecFromEncryptedBytes(byte[] bytes, String passphrase) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException, InvalidKeyException, InvalidAlgorithmParameterException {
        KeySpec keySpec;
        EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new EncryptedPrivateKeyInfo(bytes);
        Cipher cipher = Cipher.getInstance(encryptedPrivateKeyInfo.getAlgName());
        PBEKeySpec pbeKeySpec = new PBEKeySpec(passphrase.toCharArray());
        SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(encryptedPrivateKeyInfo.getAlgName());
        Key pbeKey = secretKeyFactory.generateSecret(pbeKeySpec);
        cipher.init(Cipher.DECRYPT_MODE, pbeKey, encryptedPrivateKeyInfo.getAlgParameters());
        keySpec = encryptedPrivateKeyInfo.getKeySpec(cipher);
        return keySpec;
    }

    private static void printUsageAndExit() {
        System.err.println("arguments: [-crypt] [-passin input-password] [-passout output-password] [-format format] [input-filename] [-o output-filename]");
        System.err.println();
        System.err.println("This utility converts public and private keys multidirectionally between");
        System.err.println("Handle protocol format, JWK format, and standard PEM format: public keys");
        System.err.println("in X.509 SubjectPublicKeyInfo format (with files beginning");
        System.err.println("-----BEGIN PUBLIC KEY-----) and private keys in PKCS#8 PrivateKeyInfo");
        System.err.println("format (with files beginning -----BEGIN PRIVATE KEY----- or");
        System.err.println("-----BEGIN ENCRYPTED PRIVATE KEY-----).");
        System.err.println();
        System.err.println("If input and/or output filename is omitted or -, the utility will use standard");
        System.err.println("input and/or output.");
        System.err.println();
        System.err.println("The -f or -format option can be used to specify the output format.  If omitted");
        System.err.println("Handle protocol format is assumed, unless the input has that format, in which");
        System.err.println("case PEM format is assumed.  Allowed values: jwk, pem, handle.");
        System.err.println();
        System.err.println("The -passin option argument will be used to decrypt an input encrypted private");
        System.err.println("key.  If absent the utility will ask the user for a passphrase.");
        System.err.println();
        System.err.println("If the -crypt option is given with a private key, the utility will encrypt any");
        System.err.println("private key output using the -passout option argument (if present) or using a");
        System.err.println("passphrase obtained by asking the user.");
    }

    private final byte[] bytes;
    private final boolean encrypt;
    private String outputFilename;
    private byte[] passIn;
    private byte[] passOut;
    private String format;

    // for testing
    KeyConverter(byte[] bytes, boolean encrypt, String format) {
        this.bytes = bytes;
        this.encrypt = encrypt;
        this.format = format;
    }

    public KeyConverter(byte[] bytes, boolean encrypt, String outputFilename, byte[] passIn, byte[] passOut, String format) {
        this.bytes = bytes;
        this.encrypt = encrypt;
        this.outputFilename = outputFilename;
        this.passIn = passIn;
        this.passOut = passOut;
        this.format = format;
    }

    byte[] getPassIn() throws Exception {
        if (passIn != null) return passIn;
        else return Util.getPassphrase("Enter the passphrase to decrypt the input private key: ");
    }

    byte[] getPassOut() throws Exception {
        if (passOut != null) return passOut;
        byte[] secKey;
        while (true) {
            // Read the passphrase and use it to encrypt the private key
            secKey = Util.getPassphrase("\nPlease enter the passphrase to encrypt the output private key: ");
            byte secKey2[] = Util.getPassphrase("\nPlease re-enter the private key passphrase: ");
            if (!Util.equals(secKey, secKey2)) {
                System.err.println("\nPassphrases do not match!  Try again.\n");
                continue;
            } else {
                break;
            }
        }
        return secKey;
    }

    private static void convert(String inputFilename, String outputFilename, boolean encrypt, byte[] passIn, byte[] passOut, String format) throws Exception {
        byte[] bytes;
        if (inputFilename == null) {
            bytes = StreamUtil.readFully(System.in);
        } else {
            try (InputStream in = new BufferedInputStream(new FileInputStream(inputFilename))) {
                bytes = StreamUtil.readFully(in);
            }
        }
        new KeyConverter(bytes, encrypt, outputFilename, passIn, passOut, format).convert();
    }

    void convert() throws Exception {
        if (Util.looksLikeBinary(bytes)) {
            convertFromHs();
        } else {
            convertToHs();
        }
    }

    private static boolean isPrivateKey(byte[] bytes) {
        int firstInt = Encoder.readInt(bytes, 0);
        return firstInt < Common.MAX_ENCRYPT;
    }

    private void convertFromHs() throws Exception {
        if (format == null) format = "pem";
        if (isPrivateKey(bytes)) {
            byte[] secKey = null;
            if (Util.requiresSecretKey(bytes)) {
                secKey = getPassIn();
            }
            byte[] keyBytes = Util.decrypt(bytes, secKey);
            PrivateKey privateKey = Util.getPrivateKeyFromBytes(keyBytes);
            outputPrivateKey(privateKey);
        } else {
            PublicKey publicKey = Util.getPublicKeyFromBytes(bytes);
            outputPublicKey(publicKey);
        }
    }

    private void convertToHs() throws Exception {
        if (format == null) format = "handle";
        String string = new String(bytes, "UTF-8");
        try {
            JsonObject jsonObject = new JsonParser().parse(string).getAsJsonObject();
            convertJwk(jsonObject);
            return;
        } catch (Exception e) {
            // assume PEM format
        }
        BytesAndKeyType bytesAndKeyType = readPemFile(new StringReader(string));
        @SuppressWarnings("hiding")
        byte[] bytes = bytesAndKeyType.bytes;
        if ("PUBLIC".equals(bytesAndKeyType.keyType)) {
            PublicKey publicKey = publicKeyFromBytes(bytes);
            outputPublicKey(publicKey);
        } else {
            boolean encryptedInput = "ENCRYPTED PRIVATE".equals(bytesAndKeyType.keyType);
            if (!encryptedInput && !"PRIVATE".equals(bytesAndKeyType.keyType)) {
                throw new Exception("Unrecognized input file");
            }
            String passphrase = null;
            if (encryptedInput) {
                byte[] secKey = getPassIn();
                passphrase = Util.decodeString(secKey);
            }
            PrivateKey privateKey = privateKeyFromBytes(bytes, encryptedInput, passphrase);
            outputPrivateKey(privateKey);
        }
    }

    private void convertJwk(JsonObject jwk) throws Exception {
        if (format == null) format = "handle";
        String kty = jwk.get("kty").getAsString();
        boolean isPrivate;
        if ("DSA".equalsIgnoreCase(kty)) {
            isPrivate = jwk.has("x");
        } else if ("RSA".equalsIgnoreCase(kty)) {
            isPrivate = jwk.has("d");
        } else {
            throw new Exception("Unexpected kty " + kty);
        }
        if (isPrivate) {
            PrivateKey privateKey = GsonUtility.getGson().fromJson(jwk, PrivateKey.class);
            outputPrivateKey(privateKey);
        } else {
            PublicKey publicKey = GsonUtility.getGson().fromJson(jwk, PublicKey.class);
            outputPublicKey(publicKey);
        }
    }

    private void outputPrivateKey(PrivateKey privateKey) throws Exception {
        if (!encrypt) {
            if (format.equalsIgnoreCase("pem")) {
                sendOutput(toPkcs8UnencryptedPem(privateKey));
            } else if (format.equalsIgnoreCase("jwk")) {
                sendOutput(GsonUtility.getPrettyGson().toJson(privateKey));
            } else if (format.equalsIgnoreCase("handle")) {
                sendOutput(Util.encrypt(Util.getBytesFromPrivateKey(privateKey), null, Common.ENCRYPT_NONE));
            } else {
                throw new Exception("Bad format " + format);
            }
        } else {
            byte[] secKey = getPassOut();
            if (format.equalsIgnoreCase("pem")) {
                sendOutput(toPkcs8EncryptedPem(privateKey, Util.decodeString(secKey)));
            } else if (format.equalsIgnoreCase("jwk")) {
                System.err.println("Encrypted private key not possible with format jwk");
            } else if (format.equalsIgnoreCase("handle")) {
                sendOutput(Util.encrypt(Util.getBytesFromPrivateKey(privateKey), secKey));
            } else {
                throw new Exception("Bad format " + format);
            }
        }
    }

    private void outputPublicKey(PublicKey publicKey) throws Exception, HandleException {
        if (format.equalsIgnoreCase("pem")) {
            sendOutput(toX509Pem(publicKey));
        } else if (format.equalsIgnoreCase("jwk")) {
            sendOutput(GsonUtility.getPrettyGson().toJson(publicKey));
        } else if (format.equalsIgnoreCase("handle")) {
            sendOutput(Util.getBytesFromPublicKey(publicKey));
        } else {
            throw new Exception("Bad format " + format);
        }
    }

    void sendOutput(String string) throws Exception {
        if (outputFilename == null) {
            Writer writer = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));
            writer.write(string);
            writer.flush();
        } else {
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilename), "UTF-8"))) {
                writer.write(string);
            }
        }
    }

    void sendOutput(@SuppressWarnings("hiding") byte[] bytes) throws Exception {
        if (outputFilename == null) {
            OutputStream out = new BufferedOutputStream(System.out);
            out.write(bytes);
            out.flush();
        } else {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename))) {
                out.write(bytes);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        SimpleCommandLine commandLine = new SimpleCommandLine("o", "output", "passin", "passout", "f", "format");
        commandLine.parse(args);
        if (commandLine.hasOption("h") || commandLine.hasOption("help")) {
            printUsageAndExit();
            return;
        }

        boolean encrypt = commandLine.hasOption("crypt");

        String passInString = commandLine.getOptionArgument("passin");
        byte[] passIn = null;
        if (passInString != null) passIn = Util.encodeString(passInString);

        String passOutString = commandLine.getOptionArgument("passout");
        byte[] passOut = null;
        if (passOutString != null) passOut = Util.encodeString(passOutString);

        String outputFilename = commandLine.getOptionArgument("output");
        if (outputFilename == null) outputFilename = commandLine.getOptionArgument("o");
        if (outputFilename == null && commandLine.getOperands().size() >= 2) outputFilename = commandLine.getOperands().get(1);
        if ("-".equals(outputFilename)) outputFilename = null;

        String inputFilename = null;
        if (!commandLine.getOperands().isEmpty()) inputFilename = commandLine.getOperands().get(0);
        if ("-".equals(inputFilename)) inputFilename = null;

        String format = commandLine.getOptionArgument("format");
        if (format == null) format = commandLine.getOptionArgument("f");

        convert(inputFilename, outputFilename, encrypt, passIn, passOut, format);
    }
}
