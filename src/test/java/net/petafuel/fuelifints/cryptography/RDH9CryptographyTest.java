package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import javax.crypto.Cipher;
import java.security.PublicKey;
import java.security.Security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RDH9CryptographyTest {

    private static final Logger LOG = LogManager.getLogger(RDH9CryptographyTest.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testDecrypt() throws Exception {
        byte[] data = "Hello World!".getBytes("ISO-8859-1");

        KeyManager keyManager = KeyManager.getInstance("DEBUG");
        PublicKey bankPublicKey = keyManager.getPublicKey(SecurityMethod.RDH_9, "V");

        TwoKeyTripleDESCryptography twoKeyTripleDESCryptography = new TwoKeyTripleDESCryptography();
        byte[] tripleDesKey = twoKeyTripleDESCryptography.generatePassword();

        LOG.info("TripleDESKeyLength: " + tripleDesKey.length);

        Cipher twoKeyTripleDesCipher = twoKeyTripleDESCryptography.createCipher(tripleDesKey, Cipher.ENCRYPT_MODE);
        byte[] encryptedMessage = twoKeyTripleDesCipher.doFinal(data);

        Cipher rsaCipher = Cipher.getInstance("RSA/NONE/PKCS1Padding");
        rsaCipher.init(Cipher.ENCRYPT_MODE, bankPublicKey);

        byte[] encryptedKey = rsaCipher.doFinal(tripleDesKey);

        LOG.info("EncryptedKeyBytesLength: " + encryptedKey.length);

        RDH9Cryptography rdh9Decryptor = new RDH9Cryptography();
        byte[] decryptedData = rdh9Decryptor.decrypt(encryptedMessage, encryptedKey, "DEBUG");

        assertNotNull(decryptedData);
        assertArrayEquals(data, decryptedData);
    }

    @Test
    public void testEncryptMessage() throws Exception {
        byte[] data = "hello world!".getBytes();
        RDH9Cryptography rdh9Cryptography = new RDH9Cryptography();
        byte[] encryptionKey = rdh9Cryptography.generateKey();
        byte[] encrypted = rdh9Cryptography.encryptMessage(data, encryptionKey, "DEBUG");
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);
    }

    @Test
    public void testGenerateKey() throws Exception {
        RDH9Cryptography rdh9Cryptography = new RDH9Cryptography();
        byte[] encryptionKey = rdh9Cryptography.generateKey();
        assertNotNull(encryptionKey);
        assertTrue(encryptionKey.length > 0);
    }
}
