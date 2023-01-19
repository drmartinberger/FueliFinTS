package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.junit.Test;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class CryptographyFactoryTest {

    @Test
    public void testGetPinTanDecryptor() throws Exception {
        Cryptography cryptography = CryptographyFactory.getCryptography(SecurityMethod.PIN_2);
        assertNotNull(cryptography);
        assertTrue(cryptography instanceof PinTanCryptography);
    }

    @Test
    public void testGetRDH10Decryptor() {
        Cryptography cryptography = CryptographyFactory.getCryptography(SecurityMethod.RDH_10);
        assertNotNull(cryptography);
        assertTrue(cryptography instanceof RDH10Cryptography);
    }
}
