package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;

import javax.crypto.Cipher;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

public class RDH10DecryptorTest {

    private static final Logger LOG = LogManager.getLogger(RDH10DecryptorTest.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    public void testDecrypt() throws Exception {

        byte[] data = new byte[256];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(data);

        KeyManager keyManager = KeyManager.getInstance("DEBUG");
        PublicKey bankPublicKey = keyManager.getPublicKey(SecurityMethod.RDH_10, "V");

        TwoKeyTripleDESCryptography twoKeyTripleDESCryptography = new TwoKeyTripleDESCryptography();
        byte[] tripleDesKey = twoKeyTripleDESCryptography.generatePassword();

        LOG.info("TripleDESKeyLength: " + tripleDesKey.length);

        Cipher twoKeyTripleDesCipher = twoKeyTripleDESCryptography.createCipher(tripleDesKey, Cipher.ENCRYPT_MODE);
        byte[] encryptedMessage = twoKeyTripleDesCipher.doFinal(data);

        Cipher rsaCipher = Cipher.getInstance("RSA/NONE/NOPADDING");
        rsaCipher.init(Cipher.ENCRYPT_MODE, bankPublicKey);

        byte[] encryptedKey = rsaCipher.doFinal(tripleDesKey);

        LOG.info("EncryptedKeyBytesLength: " + encryptedKey.length);

        RDH10Cryptography rdh10Decryptor = new RDH10Cryptography();
        byte[] decryptedData = rdh10Decryptor.decrypt(encryptedMessage, encryptedKey, "DEBUG");

        assertNotNull(decryptedData);
        assertArrayEquals(data, decryptedData);
    }
}
