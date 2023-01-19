package net.petafuel.fuelifints.protocol.fints3;

import net.petafuel.fuelifints.cryptography.Cryptography;
import net.petafuel.fuelifints.cryptography.CryptographyFactory;
import net.petafuel.fuelifints.exceptions.ElementParseException;
import net.petafuel.fuelifints.exceptions.HBCISyntaxException;
import net.petafuel.fuelifints.exceptions.HBCIValidationException;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.protocol.FinTSPayload;
import net.petafuel.fuelifints.protocol.IFinTSDecryptor;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.HNHBK;
import net.petafuel.fuelifints.protocol.fints3.segments.HNHBS;
import net.petafuel.fuelifints.protocol.fints3.segments.HNVSD;
import net.petafuel.fuelifints.protocol.fints3.segments.HNVSK;
import net.petafuel.fuelifints.protocol.fints3.segments.SegmentUtil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsprofil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Verschluesselungsalgorithmus;
import net.petafuel.fuelifints.support.ByteSplit;
import net.petafuel.fuelifints.support.LogHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * - prüft die Syntax der Steuersegmente
 * - entschlüsselt die Nachricht
 * - prüft die Signatur
 * <p/>
 * Diese Klasse tastet die eigentlichen Aufträge nicht an, das wird später vom Parser erledigt
 */
public class FinTS3Decryptor implements IFinTSDecryptor {

    private static final Logger LOG = LogManager.getLogger(FinTS3Decryptor.class);
    private FinTSPayload payload;
    private Dialog dialog;
    private HNHBK hnhbk;
    private HNHBS hnhbs;
    private HNVSK hnvsk;

    @Override
    public void run() {
        //3 Schritte:
        //Nachrichtenkopf & Nachrichtenabschluss prüfen (HNHBK & HNHBS)  FIXED
        //Verschlüsselungskopf prüfen (HNVSK) -> später irgendwann bei RDH auch entschlüsseln FIXED
        //Signatur überprüfen (HNSHK, HNSHA) -> für RDH den Hashwert checken (später), für PIN/TAN die PIN, ev. TAN checken WONT-FIX
        //
        //Diese Logik steckt zur Zeit noch (teilweise) in protocol.fints3.segments.SegmentManager und muss da raus

        List<byte[]> segments = null;
        try {
            segments = ByteSplit.split(payload.getPayload(), ByteSplit.MODE_SEGMENT);
        } catch (HBCISyntaxException e) {
            throw new RuntimeException(e);
        }
        try {
            LOG.trace("Anzahl segmente: " + segments.size() + " von Auftrag:");
            for (byte[] seg : segments) {
                LOG.trace(LogHelper.removeSignatureInformation(new String(seg, "ISO-8859-1")));
            }
        } catch (UnsupportedEncodingException e) {
        }
        boolean messageSizeValid = false;
        try {
            messageSizeValid = messageSizeValid || checkMessageSize(segments.get(0));
        } catch (ElementParseException e) {
            LOG.error("ElementParseException", e);
        }

        if (messageSizeValid) {
            LOG.trace("MessageSize valid");
        } else {
            LOG.error("MessageSize invalid", new HBCIValidationException("Nachrichtenlänge ungültig."));
            throw new RuntimeException(new HBCIValidationException("Nachrichtenlänge ungültig."));
        }

        try {
            checkHnhbk();
        } catch (HBCIValidationException ex) {
            throw new RuntimeException(ex);
        }

        boolean messageNumberValid = false;
        try {
            messageNumberValid = messageNumberValid || checkHnhbs(segments.get(segments.size() - 1));
        } catch (UnsupportedEncodingException e) {
            LOG.error("UnsupportedEncodingException", e);
            throw new RuntimeException(e);
        } catch (ElementParseException e) {
            LOG.error("ElementParseException", e);
            throw new RuntimeException(e);
        }

        if (messageNumberValid) {
            LOG.trace("MessageNumber valid");
        } else {
            LOG.error("MessageNumber invalid", new HBCIValidationException("Nachrichtenummer in HNHBK und HNHBS stimmen nicht überein."));
            throw new RuntimeException(new HBCIValidationException("Nachrichtenummer in HNHBK und HNHBS stimmen nicht überein."));
        }

        if (SegmentUtil.getSegmentName(segments.get(1)).equals("HNVSK")) {
            /*
             * Verschlüsselte Nachricht erhalten.
             */
            LOG.trace("Check if message is PinTan or HBCI (encrypted).");
            String securityMethod = "";
            try {
                securityMethod = getSecurityMethod(segments.get(1));
            } catch (ElementParseException e) {
                throw new RuntimeException(e);
            }
            Cryptography cryptography = CryptographyFactory.getCryptography(SecurityMethod.valueOf(securityMethod.replace('-', '_')));
            byte[] encrypted = new byte[0];
            try {
                encrypted = getEncryptedBytes(segments.get(2));
            } catch (UnsupportedEncodingException e) {
                //ISO-8859-1 is supported
            } catch (ElementParseException e) {
                throw new RuntimeException(e);
            }
            byte[] decryptedSegments = cryptography.decrypt(encrypted, getCypherKey(securityMethod), hnvsk.getSchluesselname().getKreditsinstitutkennung().getKreditinstitutscode());
            LOG.trace("Finished decrypting...");
            dialog.setBankId(hnvsk.getSchluesselname().getKreditsinstitutkennung().getKreditinstitutscode());

            try {
                LOG.trace("Decrypted Segments: {}", LogHelper.removeSignatureInformation(new String(decryptedSegments, "ISO-8859-1")));
            } catch (UnsupportedEncodingException e) {
                LOG.error("UnsupportedEncodingException", e);
            }

            // + 2 weil das Segment-Trennzeichen (') in HNHBK und HNHBS abgeschnitten ist.
            int newPayloadLength = hnhbk.getBytes().length + hnhbs.getBytes().length + decryptedSegments.length + 2;
            byte[] newPayload = new byte[newPayloadLength];
            int offset = 0;
            System.arraycopy(hnhbk.getBytes(), 0, newPayload, offset, hnhbk.getBytes().length);
            offset += hnhbk.getBytes().length;
            newPayload[offset] = '\'';
            offset++;
            System.arraycopy(decryptedSegments, 0, newPayload, offset, decryptedSegments.length);
            offset += decryptedSegments.length;
            System.arraycopy(hnhbs.getBytes(), 0, newPayload, offset, hnhbs.getBytes().length);
            offset += hnhbs.getBytes().length;
            newPayload[offset] = '\'';

            try {
                LOG.info("Auftragsnachricht: " + LogHelper.removeSignatureInformation(new String(newPayload, "ISO-8859-1")));
            } catch (UnsupportedEncodingException e) {
                LOG.error("UnsupportedEncodingException", e);
            }

            payload = new FinTSPayload(newPayload, payload.getTaskId());

        } else {
            //anonyme anfragen müsse nicht angefasst werden -> payload unverändert weiter reichen
        }
        FinTS3Controller.getInstance().finishedDecryption(payload, dialog);
    }

    private byte[] getEncryptedBytes(byte[] bytes) throws UnsupportedEncodingException, ElementParseException {
        //String hnvsdString = new String(bytes,0,bytes.length-1,"ISO-8859-1");
        HNVSD hnvsd = new HNVSD(bytes);
        hnvsd.parseElement();
        byte[] encryptedDE = hnvsd.getVerschluesselteDaten();
        //LOG.debug("EncryptedDE: " + new String(encryptedDE, "ISO-8859-1"));
        int offset = 1;
        for (; offset < encryptedDE.length; offset++) {
            if (encryptedDE[offset] == 0x40) {
                LOG.trace("Found binary character at: {}", offset);
                offset++;
                break;
            }
        }
        byte[] data = new byte[encryptedDE.length - offset];
        LOG.trace("EncryptedDataLength: {}", data.length);
        System.arraycopy(encryptedDE, offset, data, 0, data.length);
        return data;
    }

    private byte[] getCypherKey(String securityMethod) {
        if (securityMethod.equals("PIN-1") || securityMethod.equals("PIN-2")) {
            /*
             * Daten in HNVSD werden hier nicht verschlüsselt.
             */
            return new byte[0];
        } else {
            Verschluesselungsalgorithmus verschluesselungsalgorithmus = hnvsk.getVerschluesselungsalgorithmus();
            byte[] keyDEG = verschluesselungsalgorithmus.getWertAlgorithmusparameterSchluessel();
            //LOG.debug(new String(keyDEG));
            int index = 1;
            for (; index < keyDEG.length; index++) {
                if (keyDEG[index] == 0x40) {
                    index++;
                    break;
                }
            }

            byte[] encryptedKey = new byte[keyDEG.length - index];
            System.arraycopy(keyDEG, index, encryptedKey, 0, encryptedKey.length);
            return encryptedKey;
        }
    }

    private String getSecurityMethod(byte[] bytes) throws ElementParseException {
        //String hnvskString = new String(bytes,0,bytes.length-1,"ISO-8859-1");
        hnvsk = new HNVSK(bytes);
        hnvsk.parseElement();
        Sicherheitsprofil sicherheitsprofil = hnvsk.getSicherheitsprofil();
        return sicherheitsprofil.getSicherheitsverfahren() + "-" + sicherheitsprofil.getSicherheitsverfahrensversion();
    }

    private void checkHnhbk() throws HBCIValidationException {
        if (hnhbk != null) {
            int nachrichtennummer = hnhbk.getNachrichtennummer();
            if (nachrichtennummer <= 0) {
                HBCIValidationException exception = new HBCIValidationException("Nachrichtennummer <= 0.");
                LOG.error("Nachrichtennummer falsch.", exception);
                throw exception;
            }
            if (hnhbk.getBezugsnachricht() != null) {
                LOG.error("Bezugsnachricht vom Kunden gesetzt. Abbruch.", new HBCIValidationException("Bezugsnachricht vom Kunden gesetzt. Abbruch."));
                throw new HBCIValidationException("Bezugsnachricht vom Kunden gesetzt. Abbruch.");
            }
        }
    }

    private boolean checkHnhbs(byte[] segment) throws UnsupportedEncodingException, ElementParseException {
        //String hnhbsString = new String(segment,0,segment.length-1,"ISO-8859-1");
        //LOG.info(hnhbsString);
        hnhbs = new HNHBS(segment);
        hnhbs.parseElement();
        int nachrichtennummerHnhbk = hnhbk.getNachrichtennummer();
        int nachrichtennummerHnhbs = hnhbs.getNachrichtennummer();
        LOG.trace("Nachrichtennummer HNHBK: " + nachrichtennummerHnhbk + " Nachrichtennummer HNHBS: " + nachrichtennummerHnhbs);
        return nachrichtennummerHnhbk == nachrichtennummerHnhbs;
    }

    private boolean checkMessageSize(byte[] segment) throws ElementParseException {
        //String hnhbkSting = new String(segment,0,segment.length-1,"ISO-8859-1");
        //LOG.info(hnhbkSting);
        hnhbk = new HNHBK(segment);
        hnhbk.parseElement();
        String messageSize = hnhbk.getNachrichtengroesse();
        //int payloadSize = (new String(payload.getPayload(),"ISO-8859-1")).length();
        //LOG.info("Parsed MessageSize: "+ messageSize +" parsed int: "+ Integer.parseInt(messageSize)+ " payload size: "+ payloadSize);
        return Integer.parseInt(messageSize) == payload.getPayload().length/*(new String(payload.getPayload(),"ISO-8859-1")).length()*/;
    }

    @Override
    public void setPayload(FinTSPayload payload) {
        this.payload = payload;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }
}
