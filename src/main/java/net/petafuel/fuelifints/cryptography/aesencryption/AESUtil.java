package net.petafuel.fuelifints.cryptography.aesencryption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.util.Base64;
import java.util.Properties;

public class AESUtil {
    private static final Logger LOG = LogManager.getLogger(AESUtil.class);

    public static void main (String[] args) {
        System.out.println(aesEncrypt(args[0].getBytes()));
        System.exit(0);
    }

       /**
        * Expects a  payload to encrypt and returns the encryption result as Base64
        * @param toEncrypt
        * @return
        */
       public static String aesEncrypt(byte[] toEncrypt) {
           try {
               Cipher aesEncrypt = Cipher.getInstance("AES/CBC/PKCS5PADDING");
               SecretKey aesKey=initAESKey();
               aesEncrypt.init(Cipher.ENCRYPT_MODE, aesKey,new IvParameterSpec(new byte[16]));
               byte[] byteToEncrypt=  toEncrypt;
               byte[] encryptedData = aesEncrypt.doFinal(byteToEncrypt);
               return Base64.getEncoder().encodeToString(encryptedData);
           } catch (Exception ex) {
               LOG.error("Exception encrypt the payload", ex);
           }
           return null;
       }

       /**
        * Expects a BASE64 encoded encrypted payload and returns the decrypted result
        * as byte[]
        * @param toDecrypt
        * @return
        */
       public static byte[] aesDecrypt(String toDecrypt) {
           try {
               Cipher aesDecrypt = Cipher.getInstance("AES/CBC/PKCS5PADDING");
               SecretKey aesKey=initAESKey();
               byte[] byteToDecrypt=Base64.getDecoder().decode(toDecrypt);
               IvParameterSpec ivParameterSpec= new IvParameterSpec(new byte[16]);
               aesDecrypt.init(Cipher.DECRYPT_MODE, aesKey,ivParameterSpec);
               byte[] decryptedData = aesDecrypt.doFinal(byteToDecrypt);
               return decryptedData;
           } catch (Exception ex) {
               LOG.error("Exception decrypting the payload", ex);
           }

           return null;
       }

       private static SecretKeySpec initAESKey() {
           try {
               /**
                * Read BIC and blz from fuelibus.properties
                */
               Properties properties = new Properties();
               properties.load(new FileInputStream("config/fuelifints.properties"));
               String keyFileConfig= properties.getProperty("aes_key_location");
               Properties keyFile= new Properties();
               keyFile.load(new FileInputStream(keyFileConfig));
               String aesKeyEncoded = keyFile.getProperty("aes_key");
               byte[] aesKey = Base64.getDecoder().decode(aesKeyEncoded);
               SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
               return keySpec;
           } catch (Exception ex) {
               LOG.error("Exception", ex);
           }
           return null;

       }
}
