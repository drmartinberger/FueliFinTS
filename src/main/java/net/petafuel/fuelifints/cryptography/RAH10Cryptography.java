package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

public class RAH10Cryptography implements Cryptography {

    private static final Logger LOG = LogManager.getLogger(RAH10Cryptography.class);

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private byte[] iv = new byte[16];

    @Override
    public byte[] decrypt(byte[] encrypted, byte[] key, String bankId) {
        try {
            KeyManager keyManager = KeyManager.getInstance(bankId);
            PrivateKey privateKey = keyManager.getPrivateKey(SecurityMethod.RAH_10, "V");
            Cipher cipher = Cipher.getInstance("RSA/NONE/NOPADDING");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] symmetricKey = cipher.doFinal(key);
            LOG.debug("decryptedKeyLength: {}", symmetricKey.length);

            // Always read the symmetric key as bytes to avoid 0 removal from the beginning of the key
            // number datatypes will cut leading zeros
            byte[] temp = new byte[32];
            System.arraycopy(symmetricKey, 0, temp, 32 - symmetricKey.length, symmetricKey.length);

            symmetricKey = temp;

            SecretKeySpec algorithmParameterSpec = new SecretKeySpec(symmetricKey, "AES");

            Cipher aesCipher = Cipher.getInstance("AES/CBC/NOPADDING");
            aesCipher.init(Cipher.DECRYPT_MODE, algorithmParameterSpec, new IvParameterSpec(iv));
            return removeZkaPadding(aesCipher.doFinal(encrypted));

        } catch (NoSuchAlgorithmException e) {
            LOG.error("An Error occurred during decryption.", e.getMessage());
        } catch (NoSuchPaddingException e) {
            LOG.error("An Error occurred during decryption.", e.getMessage());
        } catch (InvalidKeyException e) {
            LOG.error("An Error occurred during decryption.", e.getMessage());
        } catch (BadPaddingException e) {
            LOG.error("An Error occurred during decryption.", e.getMessage());
        } catch (IllegalBlockSizeException e) {
            LOG.error("An Error occurred during decryption.", e.getMessage());
        } catch (InvalidAlgorithmParameterException e) {
            LOG.error("An Error occurred during decryption.", e.getMessage());
        }
        return new byte[0];
    }

    private byte[] removeZkaPadding(byte[] bytes) {
        int index = bytes.length - 1;
        for (; index >= 0; index--) {
            if (bytes[index] == (byte) 0x80) {
                break;
            }
        }
        byte[] message = new byte[index];
        System.arraycopy(bytes, 0, message, 0, message.length);
        return message;
    }

    @Override
    public byte[] encryptMessage(byte[] message, byte[] key, String bankId) {
        byte[] paddedMessage = zkaPadding(message);

        try {
            SecretKeySpec algorithmParameterSpec = new SecretKeySpec(key, "AES");
            Cipher aesCipher = Cipher.getInstance("AES/CBC/NOPADDING");
            aesCipher.init(Cipher.ENCRYPT_MODE, algorithmParameterSpec, new IvParameterSpec(iv));
            return aesCipher.doFinal(paddedMessage);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (NoSuchPaddingException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (InvalidKeyException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (BadPaddingException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (IllegalBlockSizeException e) {
            LOG.error("An Error occurred during decryption.", e);
        } catch (InvalidAlgorithmParameterException e) {
            LOG.error("An Error occurred during decryption.", e);
        }
        return new byte[0];
    }

    private byte[] zkaPadding(byte[] message) {
        byte[] paddedMessage;
        int length = (message.length + 1) % 16;
        if (length == 0) {
            paddedMessage = new byte[message.length + 1];
            System.arraycopy(message, 0, paddedMessage, 0, message.length);
            paddedMessage[paddedMessage.length - 1] = (byte) 0x80;
        } else {
            paddedMessage = new byte[message.length + 1 + (16 - length)];
            System.arraycopy(message, 0, paddedMessage, 0, message.length);
            paddedMessage[message.length] = (byte) 0x80;
        }
        return paddedMessage;
    }

    @Override
    public byte[] encryptEncryptionKey(byte[] encryptionKey, String bankId, String userId) {
        KeyManager keyManager = KeyManager.getInstance(bankId);
        PublicKey publicKey = keyManager.getUserPublicKey(userId, null, SecurityMethod.RAH_10, "V");
        try {
            Cipher cipher = Cipher.getInstance("RSA/NONE/NOPADDING");
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
        SecureRandom secureRandom = new SecureRandom();
        byte[] secretKey = new byte[32];
        secureRandom.nextBytes(secretKey);
        //starmoney key fix
        if (secretKey[0] == 0) {
            secretKey[0] = 1;
        }
        LOG.info("SecretKeyLength: {}", secretKey.length);
        return secretKey;
    }
}
