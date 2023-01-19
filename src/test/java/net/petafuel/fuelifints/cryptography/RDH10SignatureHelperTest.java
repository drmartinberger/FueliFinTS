package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.dataaccess.DummyAccessFacade;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.HNSHA;
import net.petafuel.fuelifints.protocol.fints3.segments.HNSHK;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;

import static org.junit.Assert.assertTrue;

public class RDH10SignatureHelperTest {


    DummyAccessFacade dummyAccessFacade = new DummyAccessFacade(null);

    @Before
    public void before() {
        dummyAccessFacade.clearUserKeys();
    }

    @After
    public void after() {
        dummyAccessFacade.clearUserKeys();
    }

    @Test
    public void testValidateSignature() throws Exception {
        KeyManager keyManager = KeyManager.getInstance("DEBUG");
        byte[] hnshkBytes = "HNSHK:2:4+RDH:10+2+123456+1+1+1::2+3234+1:20020701:111144+1:999:1+6:10:16+280:DEBUG:54321:S:0:0".getBytes("ISO-8859-1");
        byte[] toSign = ("HNSHK:2:4+RDH:10+2+123456+1+1+1::2+3234+1:20020701:111144+1:999:1+6:10:16+280:DEBUG:54321:S:0:0'" +
                "HKIDN:3:2+280:10020030+54321+2+1'" +
                "HKVVB:4:2+2+3+1+123 Banking Android+1.0'").getBytes("ISO-8859-1");

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        int bits = 4096;
        keyPairGenerator.initialize(bits, secureRandom);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey userSigningPrivateKey = keyPair.getPrivate();
        PublicKey userSigningPublicKey = keyPair.getPublic();

        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) userSigningPrivateKey;

        keyManager.addUserPublicKey("54321", SecurityMethod.RDH_10, "S", 0, 0, userSigningPublicKey);

        RDH10SignatureHelper rdh10SignatureHelper = new RDH10SignatureHelper();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(toSign);

        PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), hash.length);
        signer.init(true, new RSAKeyParameters(true, rsaPrivateKey.getModulus(), rsaPrivateKey.getPrivateExponent()));

        signer.update(hash, 0, hash.length);

        HNSHK hnshk = new HNSHK(hnshkBytes);
        hnshk.parseElement();
        byte[] signatureByes = signer.generateSignature();

        byte[] hnshaBegin = ("HNSHA:5:2+654321+@" + signatureByes.length + "@").getBytes("ISO-8859-1");
        byte[] hnshaEnd = "".getBytes("ISO-8859-1");
        int hnshaLength = hnshaBegin.length + signatureByes.length + hnshaEnd.length;
        byte[] hnshaBytes = new byte[hnshaLength];

        int offset = 0;
        System.arraycopy(hnshaBegin, 0, hnshaBytes, offset, hnshaBegin.length);
        offset += hnshaBegin.length;
        System.arraycopy(signatureByes, 0, hnshaBytes, offset, signatureByes.length);
        offset += signatureByes.length;
        System.arraycopy(hnshaEnd, 0, hnshaBytes, offset, hnshaEnd.length);
        HNSHA hnsha = new HNSHA(hnshaBytes);
        hnsha.parseElement();

        assertTrue(!(rdh10SignatureHelper.validateSignature(toSign, hnshk, hnsha, new Dialog()).isSuccess()));
    }
}
