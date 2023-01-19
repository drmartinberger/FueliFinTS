package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import org.junit.Test;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

public class SignatureHelperFactoryTest {
    @Test
    public void testGetPinTanSignatureHelper() throws Exception {
        SignatureHelper signatureHelper = SignatureHelperFactory.getSignatureHelper(SecurityMethod.PIN_2);
        assertNotNull(signatureHelper);
        assertTrue(signatureHelper instanceof PinTanSignatureHelper);
    }
}
