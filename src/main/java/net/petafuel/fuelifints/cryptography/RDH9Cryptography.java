package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

public class RDH9Cryptography implements Cryptography {

    private static final Logger LOG = LogManager.getLogger(RDH9Cryptography.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public byte[] decrypt(byte[] encrypted, byte[] key, String bankId) {
        try {
            KeyManager keyManager = KeyManager.getInstance(bankId);
            PrivateKey privateKey = keyManager.getPrivateKey(SecurityMethod.RDH_9, "V");
            Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] symmetricKey = cipher.doFinal(key);
            LOG.debug("Successfully decrypted the symmetric key. SymmetricKeyLength: {}", symmetricKey.length);

            Cipher cipherTwoKeyTripleDES = new TwoKeyTripleDESCryptography().createCipher(symmetricKey, Cipher.DECRYPT_MODE);

            return cipherTwoKeyTripleDES.doFinal(encrypted);
        } catch (BadPaddingException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (IllegalBlockSizeException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (NoSuchPaddingException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (InvalidKeyException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (GeneralSecurityException e) {
            LOG.error("An Error occurred during decryption.", e);
        }
        return new byte[0];
    }

    @Override
    public byte[] encryptMessage(byte[] message, byte[] key, String bankId) {
        return new RDH10Cryptography().encryptMessage(message, key, bankId);
    }

    @Override
    public byte[] encryptEncryptionKey(byte[] encryptionKey, String bankId, String userId) {
        KeyManager keyManager = KeyManager.getInstance(bankId);
        PublicKey publicKey = keyManager.getUserPublicKey(userId, null, SecurityMethod.RDH_9, "V");
        try {
            Cipher cipher = Cipher.getInstance("RSA/NONE/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(encryptionKey);
        } catch (IllegalBlockSizeException e) {
            LOG.error("An Error occurred during encryption.", e);
        } catch (BadPaddingException e) {
            LOG.error("An Error occurred during encryption.", e);
        } catch (InvalidKeyException e) {
            LOG.error("An Error occurred during encryption.", e);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("An Error occurred during encryption.", e);
        } catch (NoSuchPaddingException e) {
            LOG.error("An Error occurred during encryption.", e);
        }
        return new byte[0];
    }

    @Override
    public byte[] generateKey() {
        return new RDH10Cryptography().generateKey();
    }
}
