package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public class KeyManagerTest {
    @Test
    public void testGetInstance() throws Exception {
        KeyManager keyManager = KeyManager.getInstance("DEBUG");
    }

    @Test
    public void testGetPrivateKey() throws Exception {
        KeyManager keyManager = KeyManager.getInstance("DEBUG");
        PrivateKey cipherPrivateKey = keyManager.getPrivateKey(SecurityMethod.RDH_10, "V");
        PrivateKey signaturePrivateKey = keyManager.getPrivateKey(SecurityMethod.RDH_10, "S");
        assertNotNull(cipherPrivateKey);
        assertNotNull(signaturePrivateKey);
        assertNotEquals(cipherPrivateKey, signaturePrivateKey);
    }

    @Test
    public void testGetPublicKey() throws Exception {
        KeyManager keyManager = KeyManager.getInstance("DEBUG");
        PublicKey cipherPublicKey = keyManager.getPublicKey(SecurityMethod.RDH_10, "V");
        PublicKey signaturePublicKey = keyManager.getPublicKey(SecurityMethod.RDH_10, "S");
        assertNotNull(cipherPublicKey);
        assertNotNull(signaturePublicKey);
        assertNotEquals(cipherPublicKey, signaturePublicKey);
    }

    //@Todo Fix this Test
    public void testGetUserKey() throws NoSuchAlgorithmException {
        KeyManager keyManager = KeyManager.getInstance("DEBUG");

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        int bits = 4096;
        keyPairGenerator.initialize(bits, secureRandom);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();

        keyManager.addUserPublicKey("aUserId", SecurityMethod.RDH_10, "S", 1, 10, publicKey);
        PublicKey userPublicKey = keyManager.getUserPublicKey("aUserId", null, SecurityMethod.RDH_10, "S", 1, 10);

        assertEquals(publicKey, userPublicKey);
    }
}
