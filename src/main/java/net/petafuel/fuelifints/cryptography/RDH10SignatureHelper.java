package net.petafuel.fuelifints.cryptography;

import net.petafuel.fuelifints.dataaccess.dataobjects.ReturnDataObject;
import net.petafuel.fuelifints.exceptions.ElementParseException;
import net.petafuel.fuelifints.exceptions.HBCISyntaxException;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.HKSAK;
import net.petafuel.fuelifints.protocol.fints3.segments.HNSHA;
import net.petafuel.fuelifints.protocol.fints3.segments.HNSHK;
import net.petafuel.fuelifints.protocol.fints3.segments.SegmentUtil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Kik;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.OeffentlicherSchluessel;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Schluesselname;
import net.petafuel.fuelifints.support.ByteSplit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.signers.PSSSigner;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.List;

public class RDH10SignatureHelper implements SignatureHelper {


    private static final Logger LOG = LogManager.getLogger(RDH10SignatureHelper.class);

    @Override
    public ReturnDataObject validateSignature(byte[] bytes, HNSHK hnshk, HNSHA hnsha, Dialog dialog) {
        Schluesselname schluesselname = hnshk.getSchluesselname();
        Kik kreditinstitutkennung = schluesselname.getKreditsinstitutkennung();
        String benutzerkennung = schluesselname.getBenutzerkennung();

        KeyManager keyManager = KeyManager.getInstance(kreditinstitutkennung.getKreditinstitutscode());
        SecurityMethod securityMethod = SecurityMethod.valueOf(hnshk.getSicherheitsprofil().getSicherheitsverfahren() + "_" + hnshk.getSicherheitsprofil().getSicherheitsverfahrensversion());
        PublicKey userPublicKey = null;
        /**
         * Falls die Schlüssel gerade eingereicht werden existiert noch kein Eintrag in der Datenbank.
         * Es wird nach dem Segment gesucht welches den Signierschlüssel beinhaltet und falls vorhanden die Signatur geprüft.
         */

        if (benutzerkennung != null) {
            dialog.setUserId(benutzerkennung);
        }

        if (!keyManager.existsPublicKey(benutzerkennung, null, securityMethod, schluesselname.getSchluesselart(), schluesselname.getSchluesselversion(), schluesselname.getSchluesselnummer())) {
            List<byte[]> segments = null;
            try {
                segments = ByteSplit.split(bytes, ByteSplit.MODE_SEGMENT);
            } catch (HBCISyntaxException e) {
                return new ReturnDataObject(false, HBCISyntaxException.class.getSimpleName() + ": " + e.getCause());
            }
            if (segments == null || segments.size() == 0) {
                return new ReturnDataObject(false, "");
            }
            HKSAK hksak = null;
            for (byte[] segment : segments) {
                if ("HKSAK".equals(SegmentUtil.getSegmentName(segment))) {
                    hksak = new HKSAK(segment);
                    try {
                        hksak.parseElement();
                    } catch (ElementParseException e) {
                        return new ReturnDataObject(false, "");
                    }
                    if ("S".equals(hksak.getSchluesselname().getSchluesselart())) {
                        break;
                    } else {
                        hksak = null;
                    }
                }
            }
            if (hksak == null) {
                LOG.error("HKSAK for signingkey not found.");
                //Das benötigte Segment wurde nicht gefunden.
                return new ReturnDataObject(false, "");
            }
            OeffentlicherSchluessel oeffentlicherSchluessel = hksak.getOeffentlicherSchluessel();
            if (oeffentlicherSchluessel != null) {
                BigInteger modulus = new BigInteger(1, SegmentUtil.getBytes(oeffentlicherSchluessel.getModulus()));
                BigInteger exponent = new BigInteger(1, SegmentUtil.getBytes(oeffentlicherSchluessel.getExponent()));
                RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, exponent);
                try {
                    LOG.debug("generating publickey from data...");
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    userPublicKey = keyFactory.generatePublic(publicKeySpec);
                } catch (NoSuchAlgorithmException e) {
                    return new ReturnDataObject(false, "");
                } catch (InvalidKeySpecException e) {
                    return new ReturnDataObject(false, "");
                }
            } else {
                LOG.error("öffentlicher schlüssel nicht vorhanden..");
            }
        } else {
            LOG.debug("PublicKey should be in database...");
            if (new String(bytes).contains("HKSAK")) {
                //Schlüsseleinreichung obwohl schon Schlüssel in der Datenbank vorhanden ist:
                dialog.getLegitimationsInfo().setUserKeysSubmitted(true);
                return new ReturnDataObject(false, "");
            }
            userPublicKey = keyManager.getUserPublicKey(benutzerkennung, null, securityMethod, schluesselname.getSchluesselart(), schluesselname.getSchluesselversion(), schluesselname.getSchluesselnummer());

        }
        if (userPublicKey == null) {
            dialog.getLegitimationsInfo().setUserKeysSubmitted(true);
            LOG.error("public key not found.");
            return new ReturnDataObject(false, "");
        }
        RSAPublicKey rsaPublicKey = (RSAPublicKey) userPublicKey;

        /*
        byte[] signatureBytes = hnsha.getValidierungsresultat();
        int offset = 1;
        for (; offset < signatureBytes.length; offset++) {
            if (signatureBytes[offset] == 0x40) {
                offset++;
                break;
            }
        }
        */
        byte[] signature = SegmentUtil.getBytes(hnsha.getValidierungsresultat());//new byte[signatureBytes.length - offset];
        //System.arraycopy(signatureBytes, offset, signature, 0, signature.length);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);

            PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), hash.length);
            signer.init(false, new RSAKeyParameters(false, rsaPublicKey.getModulus(), rsaPublicKey.getPublicExponent()));
            signer.update(hash, 0, hash.length);

            boolean isSignatureVerified = signer.verifySignature(signature);

            if (isSignatureVerified) {
                dialog.getLegitimationsInfo().setStrongAuthenticated(true);
            }

            return new ReturnDataObject(isSignatureVerified, "");

            /*
            Signature signature = Signature.getInstance("SHA256withRSAandMGF1");
            signature.initVerify(userPublicKey);
            signature.update(bytes);
            return signature.verify(signatureBytes, offset, signatureBytes.length - offset);
            */
        } catch (NoSuchAlgorithmException e) {
            //SHA256withRSA is supported
        }

        return new ReturnDataObject(false, "");
    }

    @Override
    public byte[] sign(byte[] toSign, HNSHK hnshk) {
        Schluesselname schluesselname = hnshk.getSchluesselname();
        Kik kreditinstitutkennung = schluesselname.getKreditsinstitutkennung();

        KeyManager keyManager = KeyManager.getInstance(kreditinstitutkennung.getKreditinstitutscode());
        SecurityMethod securityMethod = SecurityMethod.valueOf(hnshk.getSicherheitsprofil().getSicherheitsverfahren() + "_" + hnshk.getSicherheitsprofil().getSicherheitsverfahrensversion());
        LOG.debug("verwendete Schlüsselart: {}", schluesselname.getSchluesselart());
        PrivateKey privateKey = keyManager.getPrivateKey(securityMethod, schluesselname.getSchluesselart());
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) privateKey;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(toSign);

            PSSSigner signer = new PSSSigner(new RSAEngine(), new SHA256Digest(), hash.length);
            signer.init(true, new RSAKeyParameters(true, rsaPrivateKey.getModulus(), rsaPrivateKey.getPrivateExponent()));

            signer.update(hash, 0, hash.length);

            byte[] sig = signer.generateSignature();

            return sig;
        } catch (NoSuchAlgorithmException e) {
            //SHA256withRSA is supported
        } catch (CryptoException e) {
            LOG.error("Exception", e);
        }
        return new byte[0];
    }
}
