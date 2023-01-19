package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.dataaccess.DataAccessFacade;
import net.petafuel.fuelifints.dataaccess.DataAccessFacadeManager;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Base64;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Properties;

public class KeyManager {

    private static final Logger LOG = LogManager.getLogger(KeyManager.class);

    {
        Properties properties = new Properties();
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream("config/fuelifints.properties"));
            properties.load(bis);
            bis.close();
            //RDH zeugs ...
            KEY_STORE_PATH = properties.getProperty("rdh_keystore_location");
            PRIVATE_KEY_CIPHER_ALIAS = properties.getProperty("rdh_keystore_cipher_alias");
            PRIVATE_KEY_SIGNER_ALIAS = properties.getProperty("rdh_keystore_signer_alias");
            PRIVATE_KEY_DS_ALIAS = properties.getProperty("rdh_keystore_ds_alias");
            KEY_STORE_PASSWORD = properties.getProperty("rdh_keystore_password");
            CIPHER_ALIAS_PASSWORD = properties.getProperty("rdh_keystore_cipher_password");
            SIGNER_ALIAS_PASSWORD = properties.getProperty("rdh_keystore_signer_password");
            DS_ALIAS_PASSWORD = properties.getProperty("rdh_keystore_ds_password");
        } catch (IOException ex) {
            LOG.error("Could not read from properties", ex);
        }
    }


    private static String KEY_STORE_PASSWORD;
    private static String CIPHER_ALIAS_PASSWORD;
    private static String SIGNER_ALIAS_PASSWORD;
    private static String DS_ALIAS_PASSWORD;
    private static String KEY_STORE_PATH = "rdh.ks";
    private static String PRIVATE_KEY_CIPHER_ALIAS = "rdh_cipher";
    private static String PRIVATE_KEY_SIGNER_ALIAS = "rdh_signer";
    private static String PRIVATE_KEY_DS_ALIAS = "rdh_ds";
    private static HashMap<String, KeyManager> instances = new HashMap<>();
    private String bankId;
    private DataAccessFacade dataAccessFacade;
    private PrivateKey signingPrivateKey;
    private PublicKey signingPublicKey;
    private PrivateKey cipherPrivateKey;
    private PublicKey cipherPublicKey;
    private PrivateKey dsPrivateKey;
    private PublicKey dsPublicKey;

    private KeyManager() {
    }

    /**
     * @return
     */
    public static final KeyManager getInstance(String bankId) {
        KeyManager instance = null;
        synchronized (instances) {
            instance = instances.get(bankId);
            if (instance == null) {
                instance = new KeyManager();
                instance.bankId = bankId;
                instance.dataAccessFacade = DataAccessFacadeManager.getAccessFacade(bankId);
                instances.put(bankId, instance);
            }
        }
        return instance;
    }

    public PrivateKey getPrivateKey(SecurityMethod securityMethod, String keyType) {
        if (signingPrivateKey == null) {
            readKeysFromKeyStore(keyType);
        }
        switch (keyType) {
            case "S":
                return signingPrivateKey;
            case "V":
                return cipherPrivateKey;
            case "D":
                return dsPrivateKey;
            default:
                return null;
        }
    }

    private void readKeysFromKeyStore(String keyType) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            File keyStoreFile = new File(KEY_STORE_PATH);
            keyStore.load(new FileInputStream(keyStoreFile), getKeyStorePassword());

            KeyStore.ProtectionParameter protectionParameter = new KeyStore.PasswordProtection(getPrivateKeyAliasPassword(keyType));
            KeyStore.PrivateKeyEntry privateKeyEntry;
            privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(PRIVATE_KEY_SIGNER_ALIAS, protectionParameter);
            signingPrivateKey = privateKeyEntry.getPrivateKey();
            signingPublicKey = privateKeyEntry.getCertificate().getPublicKey();
            privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(PRIVATE_KEY_CIPHER_ALIAS, protectionParameter);
            cipherPrivateKey = privateKeyEntry.getPrivateKey();
            cipherPublicKey = privateKeyEntry.getCertificate().getPublicKey();
            privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(PRIVATE_KEY_DS_ALIAS, protectionParameter);
            dsPrivateKey = privateKeyEntry.getPrivateKey();
            dsPublicKey = privateKeyEntry.getCertificate().getPublicKey();
        } catch (KeyStoreException e) {
            LOG.error("Error while accessing keystore ", e);
        } catch (CertificateException e) {
            LOG.error("Error while accessing keystore ", e);
        } catch (NoSuchAlgorithmException e) {
            LOG.error("Error while accessing keystore ", e);
        } catch (UnrecoverableEntryException e) {
            LOG.error("Error while accessing keystore ", e);
        } catch (IOException e) {
            LOG.error("Error while accessing keystore ", e);
        }

    }

    private char[] getPrivateKeyAliasPassword(String keyType) {
        if (keyType.equals("S")) {
            return SIGNER_ALIAS_PASSWORD.toCharArray();
        } else if (keyType.equals("C")) {
            return CIPHER_ALIAS_PASSWORD.toCharArray();
        } else {
            return DS_ALIAS_PASSWORD.toCharArray();
        }
    }

    private char[] getKeyStorePassword() {
        return KEY_STORE_PASSWORD.toCharArray();
    }

    public PublicKey getPublicKey(SecurityMethod securityMethod, String keyType) {
        if (!dataAccessFacade.hasKeyPair(securityMethod, keyType)) {
            readKeysFromKeyStore(keyType);
            switch (keyType) {
                case "S":
                    dataAccessFacade.updateKeyPair(securityMethod, "", new String(Base64.encode(signingPublicKey.getEncoded())), keyType, ((RSAPublicKey) signingPublicKey).getModulus(), ((RSAPublicKey) signingPublicKey).getPublicExponent());
                    break;
                case "V":
                    dataAccessFacade.updateKeyPair(securityMethod, "", new String(Base64.encode(cipherPublicKey.getEncoded())), keyType, ((RSAPublicKey) cipherPublicKey).getModulus(), ((RSAPublicKey) cipherPublicKey).getPublicExponent());
                    break;
                case "D":
                    dataAccessFacade.updateKeyPair(securityMethod, "", new String(Base64.encode(dsPublicKey.getEncoded())), keyType, ((RSAPublicKey) dsPublicKey).getModulus(), ((RSAPublicKey) dsPublicKey).getPublicExponent());
                    break;
            }
        }
        String encodedPublicKey = dataAccessFacade.getPublicKey(securityMethod, keyType);
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.decode(encodedPublicKey));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return publicKey;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    /*
    private void generateAndStoreKeyPair(SecurityMethod securityMethod, String keyType) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            int bits = 0;
            switch (securityMethod) {
                case RDH_9:
                case RDH_10:
                case RAH_9:
                case RAH_10:
                    bits = 2048;
                    break;
            }
            keyPairGenerator.initialize(bits, secureRandom);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey signingPublicKey = keyPair.getPublic();
            PrivateKey signingPrivateKey = keyPair.getPrivate();
            String base64PublicKey = new String(Base64.encode(signingPublicKey.getEncoded()));
            String base64PrivateKey = new String(Base64.encode(signingPrivateKey.getEncoded()));

            try {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(null,getKeyStorePassword());

                KeyStore.SecretKeyEntry secretKeyEntry = new KeyStore.SecretKeyEntry(signingPrivateKey);
            } catch (KeyStoreException e) {

            }

            dataAccessFacade.updateKeyPair(securityMethod, base64PrivateKey, base64PublicKey, keyType);
        } catch (NoSuchAlgorithmException e) {
        }
    }
    */

    public boolean addUserPublicKey(String userId, SecurityMethod securityMethod, String keyType, int keyVersion, int keyNumber, PublicKey publicKey) {
        String base64PublicKey = new String(Base64.encode(publicKey.getEncoded()));
        return dataAccessFacade.addUserPublicKey(userId, null, securityMethod, base64PublicKey, keyType, keyVersion, keyNumber, ((RSAPublicKey) publicKey).getModulus(), ((RSAPublicKey) publicKey).getPublicExponent());
    }

    public PublicKey getUserPublicKey(String userId, String customerId, SecurityMethod securityMethod, String keyType, int keyVersion, int keyNumber) {
        String encodedPublicKey = dataAccessFacade.getUserPublicKey(userId, customerId, securityMethod, keyType, keyVersion, keyNumber);
        if (encodedPublicKey == null) {
            return null;
        }
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.decode(encodedPublicKey));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return publicKey;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean existsPublicKey(String userId, String customerId, SecurityMethod securityMethod, String keyType, Integer keyVersion, Integer keyNumber) {
        return dataAccessFacade.existsUserKey(userId, customerId, securityMethod, keyType, keyVersion, keyNumber);
    }

    public PublicKey getUserPublicKey(String userId, String customerId, SecurityMethod securityMethod, String keyType) {
        return getUserPublicKey(userId, customerId, securityMethod, keyType, -1, securityMethod.getVersionNumber());
    }
}
