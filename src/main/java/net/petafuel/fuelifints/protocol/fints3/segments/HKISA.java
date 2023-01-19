package net.petafuel.fuelifints.protocol.fints3.segments;

import net.petafuel.fuelifints.cryptography.KeyManager;
import net.petafuel.fuelifints.model.Dialog;
import net.petafuel.fuelifints.model.IExecutableElement;
import net.petafuel.fuelifints.model.IMessageElement;
import net.petafuel.fuelifints.protocol.fints3.segments.model.SecurityMethod;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.OeffentlicherSchluessel;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Schluesselname;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Segmentkopf;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Sicherheitsprofil;
import net.petafuel.fuelifints.protocol.fints3.segments.deg.Zertifikat;
import net.petafuel.fuelifints.protocol.fints3.validator.validators.code;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedList;
import java.util.List;

/**
 * Name:  Anforderung eines öffentlichen Schlüssels
 * Typ:  Segment
 * Segmentart:  Administration
 * Kennung:  HKISA
 * Bezugssegment:        -
 * Sender:  Kunde
 */
public class HKISA extends Segment implements IExecutableElement {

    private static final Logger LOG = LogManager.getLogger(HKISA.class);

    @Element(
            description = {@ElementDescription(number = 1)})
    private Segmentkopf segmentkopf;

    @Element(
            description = {@ElementDescription(number = 2, length = 1)})
    @code(restrictions = {"2"})
    private String nachrichtenbeziehungKodiert;

    @Element(
            description = {@ElementDescription(number = 3, length = -3)})
    @code(restrictions = {"124"})
    private String bezeichnerFunktionstyp;

    @Element(
            description = {@ElementDescription(number = 4)})
    private Sicherheitsprofil sicherheitsprofil;

    @Element(
            description = {@ElementDescription(number = 5)})
    private Schluesselname schluesselname;

    @Element(
            description = {@ElementDescription(number = 6, status = ElementDescription.StatusCode.O)})
    private Zertifikat zertifikat;


    private List<IMessageElement> resultElements = new LinkedList<IMessageElement>();

    public HKISA(byte[] message) {
        super(message);
    }

    private KeyManager keyManager;

    @Override
    public StatusCode execute(Dialog dialog) {
        LOG.info("HKISA execute called");
        keyManager = KeyManager.getInstance(schluesselname.getKreditsinstitutkennung().getKreditinstitutscode());
        if (keyManager != null) {

            HIISA hiisa = new HIISA(new byte[0]);
            try {
                //if(schluesselname.getSchluesselart().equals("S")) {
                //hiisa.setSegmentkopf(new Segmentkopf(("HIISA:0:3:" + segmentkopf.getSegmentNummer()).getBytes("ISO-8859-1")));
                hiisa.setSegmentkopf(Segmentkopf.Builder.newInstance().setSegmentKennung(HIISA.class).setSegmentVersion(3).setBezugssegment(segmentkopf.getSegmentNummer()).build());
                //} else {
                //    hiisa.setSegmentkopf(new Segmentkopf(("HIISA:0:3:"+segmentkopf.getSegmentNummer()).getBytes("ISO-8859-1")));
                //}
                hiisa.setNachrichtenbeziehungKodiert("1");
                hiisa.setAustauschkontrollereferenz("0");
                hiisa.setNachrichtenreferenznummer(1234);
                hiisa.setBezeichnerFunktionstyp("224");
                Schluesselname schluesselname = new Schluesselname(new byte[0]);
                schluesselname.setKreditsinstitutkennung(this.schluesselname.getKreditsinstitutkennung());
                schluesselname.setBenutzerkennung("0");
                schluesselname.setSchluesselart(this.schluesselname.getSchluesselart());
                schluesselname.setSchluesselnummer(1);
                schluesselname.setSchluesselversion(1);
                hiisa.setSchluesselname(schluesselname);

                SecurityMethod securityMethod = SecurityMethod.valueOf(this.sicherheitsprofil.getSicherheitsverfahren() + "_" + this.sicherheitsprofil.getSicherheitsverfahrensversion());

                dialog.getLegitimationsInfo().setSecurityMethod(securityMethod);

                OeffentlicherSchluessel oeffentlicherSchluessel = new OeffentlicherSchluessel(new byte[0]);
                oeffentlicherSchluessel.setVerwendungszweckOeffentlicherSchluessel(this.schluesselname.getSchluesselart().equals("V") ? "5" : "6");
                String operationsmodusKodiert = "";
                if (this.schluesselname.getSchluesselart().equals("V")) {
                    switch (securityMethod) {
                        case RAH_10:
                        case RDH_10:
                            operationsmodusKodiert = "2";
                            break;
                        case RAH_9:
                        case RDH_9:
                            operationsmodusKodiert = "18";
                    }
                } else {
                    switch (securityMethod) {
                        case RAH_9:
                        case RAH_10:
                        case RDH_9:
                        case RDH_10:
                            operationsmodusKodiert = "19";
                            break;
                    }
                }
                oeffentlicherSchluessel.setOpertationsmodusKodiert(operationsmodusKodiert);
                oeffentlicherSchluessel.setVerfahrenBenutzer("10");
                PublicKey publicKey = keyManager.getPublicKey(securityMethod, this.schluesselname.getSchluesselart());
                RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
                BigInteger publicModulus = rsaPublicKey.getModulus();
                byte[] pubModBytes;
                if(publicModulus.toByteArray()[0] == 0) {
                    //führende Null entfernen falls vorhanden
                    pubModBytes = new byte[publicModulus.toByteArray().length - 1];
                    System.arraycopy(publicModulus.toByteArray(), 1, pubModBytes, 0, pubModBytes.length);
                } else {
                    pubModBytes = publicModulus.toByteArray();
                }
                LOG.info("pubModBytes {}", pubModBytes.length);
                byte[] publicModValue = new byte[pubModBytes.length + 2 + Integer.toString(pubModBytes.length).length()];
                publicModValue[0] = '@';
                int i = 1;
                for (byte b : Integer.toString(pubModBytes.length).getBytes("ISO-8859-1")) {
                    publicModValue[i++] = b;
                }
                publicModValue[i] = '@';
                System.arraycopy(pubModBytes, 0, publicModValue, i + 1, pubModBytes.length);
                oeffentlicherSchluessel.setModulus(publicModValue);
                oeffentlicherSchluessel.setBezeichnerModulus("12");

                BigInteger publicExponent = rsaPublicKey.getPublicExponent();
                byte[] pubExpBytes = publicExponent.toByteArray();
                byte[] publicExponentValue = new byte[pubExpBytes.length + 2 + Integer.toString(pubExpBytes.length).length()];
                publicExponentValue[0] = '@';
                i = 1;
                for (byte b : Integer.toString(pubExpBytes.length).getBytes("ISO-8859-1")) {
                    publicExponentValue[i++] = b;
                }
                publicExponentValue[i] = '@';
                System.arraycopy(pubExpBytes, 0, publicExponentValue, i + 1, pubExpBytes.length);
                oeffentlicherSchluessel.setExponent(publicExponentValue);
                oeffentlicherSchluessel.setBezeichnerExponent("13");

                hiisa.setOeffentlicherSchluessel(oeffentlicherSchluessel);
            } catch (UnsupportedEncodingException e) {
            }
            LOG.info(hiisa);
            resultElements.add(hiisa);
            return StatusCode.OK;
        }
        LOG.error("could not load keymanager in HKISA");
        return StatusCode.ERROR;
    }

    @Override
    public List<IMessageElement> getReplyMessageElements() {
        LOG.info("getReplyMessageElements called");
        return resultElements;
    }

    @Override
    public IMessageElement getStatusElement() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String toString() {
        return "HKISA{" +
                "segmentkopf=" + segmentkopf +
                ", nachrichtenbeziehungKodiert='" + nachrichtenbeziehungKodiert + '\'' +
                ", bezeichnerFunktionstyp='" + bezeichnerFunktionstyp + '\'' +
                ", sicherheitsprofil=" + sicherheitsprofil +
                ", schluesselname=" + schluesselname +
                ", zertifikat=" + zertifikat +
                ", keyManager=" + keyManager +
                '}';
    }
}
